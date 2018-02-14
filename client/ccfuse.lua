local args = { ... }

local function openSocket(uri)
    if http and http.websocket then
        return http.websocket(uri)
    end

    error("No compatible websocket API detected")
end

local openSocket = assert((http and http.websocket) or (socket and socket.websocket), "Websocket API not detected")

local json = assert(require("json"), "JSON library not detected")
local b64 = assert(require("base64"), "Base64 library not detected")

local server = assert(args[1], "Relay server URI required")
local channel = assert(args[2], "Channel required")

-- add a random value to make the URI unique
server = server .. "/" .. channel .. "/client?u=" .. math.random()

print("Connecting...")
local socket, err = assert(openSocket(server))
print("Connected, press Q to disconnect")

local errors = {
    exists = "EXISTS",
    nopath = "NOPATH",
    notdir = "NOTDIR",
    notfile = "NOTFILE",
    readonly = "READONLY",
    notempty = "NOTEMPTY",
    nospace = "NOSPACE"
}

local operations = {}

function operations.getattr(data, reply)
    assert(data.path, "Path not specified")

    reply.exists = fs.exists(data.path)
    
    if reply.exists then
        reply.isReadOnly = fs.isReadOnly(data.path)
        reply.isDir = fs.isDir(data.path)

        if not reply.isdir then
            reply.size = fs.getSize(data.path)
        end
    end
end

function operations.readdir(data, reply)
    assert(data.path, "Path not specified")

    if not fs.exists(data.path) then
        reply.error = errors.nopath
    elseif not fs.isDir(data.path) then
        reply.error = errors.notdir
    else
        reply.contents = fs.list(data.path)
    end
end

function operations.unlink(data, reply)
    assert(data.path, "Path not specified")

    if not fs.exists(data.path) then
        reply.error = errors.nopath
    elseif fs.isDir(data.path) then
        reply.error = errors.notfile
    elseif fs.isReadOnly(data.path) then
        reply.error = errors.readonly
    else
        fs.delete(data.path)
    end
end

function operations.rmdir(data, reply)
    assert(data.path, "Path not specified")

    if not fs.exists(data.path) then
        reply.error = errors.nopath
    elseif not fs.isDir(data.path) then
        reply.error = errors.notdir
    elseif fs.isReadOnly(data.path) then
        reply.error = errors.readonly
    elseif #fs.list(data.path) ~= 0 then
        reply.error = errors.notempty
    else
        fs.delete(data.path)
    end
end

function operations.rename(data, reply)
    assert(data.from, "Source not specified")
    assert(data.to, "Destination not specified")

    if not fs.exists(data.from) then
        reply.error = errors.nopath
    elseif fs.isReadOnly(data.from) or fs.isReadOnly(data.to) then
        reply.error = errors.readonly
    else
        fs.move(data.from, data.to)
    end
end

function operations.mkdir(data, reply)
    assert(data.path, "Path not specified")
    
    if fs.exists(data.path) then
        reply.error = errors.exists
    elseif fs.isReadOnly(fs.getDir(data.path)) then
        reply.error = errors.readonly
    else
        fs.makeDir(data.path)
    end
end

function operations.access(data, reply)
    assert(data.path, "Path not specified")

    if not fs.exists(data.path) then
        reply.error = errors.nopath
    end
end

operations.open = operations.access

function operations.truncate(data, reply)
    assert(data.path, "Path not specified")
    assert(data.size, "Size not specified")

    if not fs.exists(data.path) then
        reply.error = errors.nopath
    elseif fs.isDir(data.path) then
        reply.error = errors.notfile
    elseif fs.isReadOnly(data.path) then
        reply.error = errors.readonly
    else
        if data.size <= 0 then
            local f = fs.open(data.path, "wb")
            f.close()
        else
            local change = data.size - fs.getSize(data.path)

            if change > 0 then
                if change > fs.getFreeSpace(fs.getDir(data.path)) then
                    reply.error = errors.nospace
                else
                    local f = fs.open(data.path, "ab")
                    while change > 0 do
                        f.write(0)
                        change = change - 1
                    end
                    f.close()
                end
            elseif change < 0 then
                local buffer = {}
                local totalRead = 0

                local f = fs.open(data.path, "rb")
                while true do
                    local b = f.read()
                    if b ~= nil and totalRead < data.size then
                        table.insert(buffer, b)
                        totalRead = totalRead + 1
                    else
                        break
                    end
                end
                f.close()

                local f = fs.open(data.path, "wb")
                for i = 1, data.size do
                    f.write(buffer[i] or 0)
                end
                f.close()
            end
        end
    end
end

function operations.read(data, reply)
    assert(data.path, "Path not specified")
    assert(data.size, "Size not specified")
    assert(data.offset, "Offset not specified")

    if not fs.exists(data.path) then
        reply.error = errors.nopath
    elseif fs.isDir(data.path) then
        reply.error = errors.notfile
    else
        local f = fs.open(data.path, "r")
        local raw = f.readAll()
        f.close()

        reply.data = b64.encode(raw:sub(data.offset + 1, data.offset + data.size))
    end
end

function operations.write(data, reply)
    assert(data.path, "Path not specified")
    assert(data.offset, "Offset not specified")
    assert(data.data, "Data not specified")

    if fs.isDir(data.path) then
        reply.error = errors.notfile
    elseif fs.isReadOnly(data.path) or fs.isReadOnly(fs.getDir(data.path)) then
        reply.error = errors.readonly
    else
        local toWrite = b64.decode(data.data)

        if not fs.exists(data.path) then
            -- todo: figure out if offset matters here?
            local f = fs.open(data.path, "w")
            f.write(toWrite)
            f.close()
        else
            local oldData

            local f = fs.open(data.path, "r")
            oldData = f.readAll()
            f.close()

            -- todo: figure out what happens with offsets > old file length?
            local newData = oldData:sub(1, data.offset) .. toWrite .. oldData:sub(data.offset + #toWrite + 1)

            local sizeChange = #newData - #oldData

            if fs.getFreeSpace(fs.getDir(data.path)) < sizeChange then
                reply.error = errors.nospace
            else
                local f = fs.open(data.path, "w")
                f.write(newData)
                f.close()
            end
        end
    end
end

function operations.create(data, reply)
    assert(data.path, "Path not specified")

    if fs.exists(data.path) then
        reply.error = errors.exists
    elseif fs.isReadOnly(fs.getDir(data.path)) then
        reply.error = errors.readonly
    else
        local f = fs.open(data.path, "w")
        f.close()
    end
end

local ok, err = pcall(function()
    while true do
        local evt = { os.pullEventRaw() }

        if evt[1] == "terminate" or (evt[1] == "char" and evt[2]:lower() == "q") or (evt[1] == "websocket_closed" and evt[2] == server) then
            break
        end

        if evt[1] == "websocket_message" and evt[2] == server then
            local data = json.decode(evt[3])
            
            assert(data, "No message received")
            assert(data.operation and operations[data.operation], "Unknown operation: " .. data.operation or "[nil]")
            assert(data.id, "Message ID missing")

            local reply = {
                id = data.id
            }

            operations[data.operation](data, reply)

            socket.send(json.encode(reply))
        end
    end
end)

if not ok then printError(err) end

socket.close()
print("Disconnected.")