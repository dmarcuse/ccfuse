local sc = string.char
local mc = math.ceil
local bitlib = bit or bit32
local bls, brs, band = (bitlib.blshift or bitlib.lshift), (bitlib.brshift or bitlib.rshift), bitlib.band
local tc = table.concat

local chars = {}

for i = 0, 25 do
	chars[i] = sc(65 + i)
end

for i = 0, 25 do
	chars[i + 26] = sc(97 + i)
end

for i = 0, 9 do
	chars[i + 52] = sc(48 + i)
end

chars[62] = '+'
chars[63] = '/'

local revchars = {}

for k, v in pairs(chars) do
	revchars[v] = k
end

local function encode(str)
	local out = {}
	local a, b, c
	for i = 0, mc(#str / 3 - 1) do
		a = str:sub(i * 3 + 1, i * 3 + 1):byte()
		b = str:sub(i * 3 + 2, i * 3 + 2):byte()
		c = str:sub(i * 3 + 3, i * 3 + 3):byte()
		out[#out+1] = chars[brs(a, 2)]
		if b then
			out[#out+1] = chars[bls(band(a, 3), 4) + brs(b, 4)]
			if c then
				out[#out+1] = chars[bls(band(b, 0xf), 2) + brs(c, 6)]

				out[#out+1] = chars[band(c, 63)]
			else
				out[#out+1] = chars[bls(band(b, 0xf), 2)]
				out[#out+1] = '='
			end
		else
			out[#out+1] = chars[bls(band(a, 3), 4)]
			out[#out+1] = '='
			out[#out+1] = '='
		end
	end
	return tc(out)
end

local function decode(str)
	local out = {}
	local a, b, c, d
	for i = 0, mc(#str / 4 - 1) do
		a = revchars[str:sub(i * 4 + 1, i * 4 + 1)]
		b = revchars[str:sub(i * 4 + 2, i * 4 + 2)]
		c = revchars[str:sub(i * 4 + 3, i * 4 + 3)]
		d = revchars[str:sub(i * 4 + 4, i * 4 + 4)]
		if a and b then
			out[#out+1] = sc(bls(a, 2) + brs(b, 4))
			if c then
				out[#out+1] = sc(bls(band(b, 15), 4) + brs(c, 2))
				if d then
					out[#out+1] = sc(bls(band(c, 3), 6) + d)
				end
			end
		end
	end
	return tc(out)
end

return {encode=encode, decode=decode}
