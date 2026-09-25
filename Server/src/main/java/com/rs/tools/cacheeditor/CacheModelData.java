package com.rs.tools.cacheeditor;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Standalone revision-830 model geometry for CacheEditor.
 * Mirrors the verified-static Matrix3 client Class159 byte layout without
 * depending on the running client renderer.
 */
final class CacheModelData {

	final byte[] rawBytes;
	final int version;
	final int[] vertexX, vertexY, vertexZ;
	final int[] faceA, faceB, faceC;
	final short[] faceColours, faceTextures;
	final byte[] faceAlpha, faceRenderTypes;

	private CacheModelData(byte[] rawBytes, int version, int[] vertexX, int[] vertexY, int[] vertexZ,
			int[] faceA, int[] faceB, int[] faceC, short[] faceColours, short[] faceTextures,
			byte[] faceAlpha, byte[] faceRenderTypes) {
		this.rawBytes = rawBytes;
		this.version = version;
		this.vertexX = vertexX;
		this.vertexY = vertexY;
		this.vertexZ = vertexZ;
		this.faceA = faceA;
		this.faceB = faceB;
		this.faceC = faceC;
		this.faceColours = faceColours;
		this.faceTextures = faceTextures;
		this.faceAlpha = faceAlpha;
		this.faceRenderTypes = faceRenderTypes;
	}

	static CacheModelData decode(byte[] data) {
		if (data == null || data.length < 29)
			throw new IllegalArgumentException("Model data is empty or too short.");
		if ((data[0] & 0xff) != 1)
			throw new IllegalArgumentException("Unsupported model format marker " + (data[0] & 0xff)
					+ ". This viewer currently targets Matrix3 revision-830 versioned models.");

		Cursor prefix = new Cursor(data, 0);
		prefix.u8();
		prefix.u8();
		int version = prefix.u8();

		Cursor footer = new Cursor(data, data.length - 26);
		int vertexCount = footer.u16();
		int faceCount = footer.u16();
		int textureCount = footer.u16();
		int flags = footer.u8();
		boolean hasRenderTypes = (flags & 0x1) != 0;
		boolean extendedVertexGroups = (flags & 0x10) != 0;
		boolean extendedFaceGroups = (flags & 0x20) != 0;
		int priorityMode = footer.u8();
		int alphaMode = footer.u8();
		int faceGroupMode = footer.u8();
		int textureMode = footer.u8();
		int vertexGroupMode = footer.u8();
		int xDataLength = footer.u16();
		int yDataLength = footer.u16();
		int zDataLength = footer.u16();
		int faceIndexDataLength = footer.u16();
		int textureIndexDataLength = footer.u16();
		int vertexGroupDataLength = footer.u16();
		int faceGroupDataLength = footer.u16();

		if (!extendedVertexGroups)
			vertexGroupDataLength = vertexGroupMode == 1 ? vertexCount : 0;
		if (!extendedFaceGroups)
			faceGroupDataLength = faceGroupMode == 1 ? faceCount : 0;

		int type0Textures = 0, type1To3Textures = 0, type2Textures = 0;
		if (textureCount > 0) {
			Cursor textureTypes = new Cursor(data, 3);
			for (int i = 0; i < textureCount; i++) {
				int type = textureTypes.s8();
				if (type == 0) type0Textures++;
				if (type >= 1 && type <= 3) type1To3Textures++;
				if (type == 2) type2Textures++;
			}
		}

		int offset = 3 + textureCount;
		int vertexFlagsOffset = offset; offset += vertexCount;
		int renderTypeOffset = offset; if (hasRenderTypes) offset += faceCount;
		int faceIndexTypeOffset = offset; offset += faceCount;
		int priorityOffset = offset; if (priorityMode == 255) offset += faceCount;
		int faceGroupOffset = offset; offset += faceGroupDataLength;
		int vertexGroupOffset = offset; offset += vertexGroupDataLength;
		int alphaOffset = offset; if (alphaMode == 1) offset += faceCount;
		int faceIndexDeltaOffset = offset; offset += faceIndexDataLength;
		int textureIdOffset = offset; if (textureMode == 1) offset += faceCount * 2;
		int textureIndexOffset = offset; offset += textureIndexDataLength;
		int faceColourOffset = offset; offset += faceCount * 2;
		int xOffset = offset; offset += xDataLength;
		int yOffset = offset; offset += yDataLength;
		int zOffset = offset; offset += zDataLength;

		offset += type0Textures * 6;
		offset += type1To3Textures * 6;
		int textureScaleBytes = version == 14 ? 7 : version >= 15 ? 9 : 6;
		offset += type1To3Textures * textureScaleBytes;
		offset += type1To3Textures;
		offset += type1To3Textures;
		offset += type1To3Textures + type2Textures * 2;
		if (xOffset < 0 || yOffset < 0 || zOffset < 0 || offset > data.length)
			throw new IllegalArgumentException("Model layout exceeds source bytes.");

		int[] vx = new int[vertexCount], vy = new int[vertexCount], vz = new int[vertexCount];
		Cursor vertexFlags = new Cursor(data, vertexFlagsOffset);
		Cursor xData = new Cursor(data, xOffset), yData = new Cursor(data, yOffset), zData = new Cursor(data, zOffset);
		int lastX = 0, lastY = 0, lastZ = 0;
		for (int i = 0; i < vertexCount; i++) {
			int vertexFlag = vertexFlags.u8();
			if ((vertexFlag & 0x1) != 0) lastX += xData.smartSigned();
			if ((vertexFlag & 0x2) != 0) lastY += yData.smartSigned();
			if ((vertexFlag & 0x4) != 0) lastZ += zData.smartSigned();
			vx[i] = lastX; vy[i] = lastY; vz[i] = lastZ;
		}

		short[] colours = new short[faceCount], textures = new short[faceCount];
		byte[] alpha = new byte[faceCount], renderTypes = new byte[faceCount];
		for (int i = 0; i < faceCount; i++) textures[i] = -1;
		Cursor colourData = new Cursor(data, faceColourOffset);
		Cursor renderData = hasRenderTypes ? new Cursor(data, renderTypeOffset) : null;
		Cursor alphaData = alphaMode == 1 ? new Cursor(data, alphaOffset) : null;
		Cursor textureData = textureMode == 1 ? new Cursor(data, textureIdOffset) : null;
		for (int i = 0; i < faceCount; i++) {
			colours[i] = (short) colourData.u16();
			if (renderData != null) renderTypes[i] = (byte) renderData.s8();
			if (alphaData != null) alpha[i] = (byte) alphaData.s8();
			if (textureData != null) textures[i] = (short) (textureData.u16() - 1);
		}

		int[] fa = new int[faceCount], fb = new int[faceCount], fc = new int[faceCount];
		Cursor indexDelta = new Cursor(data, faceIndexDeltaOffset);
		Cursor indexType = new Cursor(data, faceIndexTypeOffset);
		int a = 0, b = 0, c = 0, accumulator = 0;
		for (int i = 0; i < faceCount; i++) {
			int type = indexType.u8() & 0x7;
			if (type == 1) {
				a = accumulator + indexDelta.smartSigned(); accumulator = a;
				b = accumulator + indexDelta.smartSigned(); accumulator = b;
				c = accumulator + indexDelta.smartSigned(); accumulator = c;
			} else if (type == 2) {
				b = c; c = accumulator + indexDelta.smartSigned(); accumulator = c;
			} else if (type == 3) {
				a = c; c = accumulator + indexDelta.smartSigned(); accumulator = c;
			} else if (type == 4) {
				int swap = a; a = b; b = swap; c = accumulator + indexDelta.smartSigned(); accumulator = c;
			} else {
				throw new IllegalArgumentException("Unsupported face index type " + type + " at face " + i + ".");
			}
			if (a < 0 || b < 0 || c < 0 || a >= vertexCount || b >= vertexCount || c >= vertexCount)
				throw new IllegalArgumentException("Face " + i + " references a vertex outside the model.");
			fa[i] = a; fb[i] = b; fc[i] = c;
		}
		return new CacheModelData(data.clone(), version, vx, vy, vz, fa, fb, fc, colours, textures, alpha, renderTypes);
	}

	int getVertexCount() { return vertexX.length; }
	int getFaceCount() { return faceA.length; }

	int getTexturedFaceCount() {
		int count = 0;
		for (short texture : faceTextures) if (texture != -1) count++;
		return count;
	}

	int[] getBounds() {
		if (vertexX.length == 0) return new int[] {0,0,0,0,0,0};
		int minX = vertexX[0], maxX = vertexX[0], minY = vertexY[0], maxY = vertexY[0], minZ = vertexZ[0], maxZ = vertexZ[0];
		for (int i = 1; i < vertexX.length; i++) {
			minX = Math.min(minX, vertexX[i]); maxX = Math.max(maxX, vertexX[i]);
			minY = Math.min(minY, vertexY[i]); maxY = Math.max(maxY, vertexY[i]);
			minZ = Math.min(minZ, vertexZ[i]); maxZ = Math.max(maxZ, vertexZ[i]);
		}
		return new int[] {minX,minY,minZ,maxX,maxY,maxZ};
	}

	void exportRaw(File file) throws IOException { Files.write(file.toPath(), rawBytes); }

	void exportObj(File objFile) throws IOException {
		String fileName = objFile.getName();
		int dot = fileName.lastIndexOf('.');
		String baseName = dot > 0 ? fileName.substring(0, dot) : fileName;
		File mtlFile = new File(objFile.getAbsoluteFile().getParentFile(), baseName + ".mtl");
		Map<Integer,String> materials = new LinkedHashMap<Integer,String>();
		for (int i = 0; i < faceColours.length; i++) {
			if (faceRenderTypes[i] == 2) continue;
			int key = faceColours[i] & 0xffff;
			if (!materials.containsKey(key)) materials.put(key, "rs_colour_" + key);
		}

		StringBuilder obj = new StringBuilder();
		obj.append("# Matrix3 CacheEditor OBJ export\n");
		obj.append("# Textured faces retain source texture ids as comments.\n");
		obj.append("mtllib ").append(mtlFile.getName()).append('\n');
		for (int i = 0; i < vertexX.length; i++)
			obj.append(String.format(Locale.US, "v %d %d %d%n", vertexX[i], -vertexY[i], vertexZ[i]));
		String currentMaterial = null;
		for (int i = 0; i < faceA.length; i++) {
			if (faceRenderTypes[i] == 2) continue;
			String material = materials.get(faceColours[i] & 0xffff);
			if (!material.equals(currentMaterial)) { obj.append("usemtl ").append(material).append('\n'); currentMaterial = material; }
			if (faceTextures[i] != -1) obj.append("# rs_texture_id ").append(faceTextures[i] & 0xffff).append('\n');
			obj.append("f ").append(faceA[i] + 1).append(' ').append(faceB[i] + 1).append(' ').append(faceC[i] + 1).append('\n');
		}

		StringBuilder mtl = new StringBuilder("# Matrix3 CacheEditor material export\n");
		for (Map.Entry<Integer,String> entry : materials.entrySet()) {
			Color color = hslToRgb((short)(int)entry.getKey());
			mtl.append("newmtl ").append(entry.getValue()).append('\n');
			mtl.append(String.format(Locale.US, "Kd %.6f %.6f %.6f%n", color.getRed()/255.0, color.getGreen()/255.0, color.getBlue()/255.0));
			mtl.append("Ka 0.000000 0.000000 0.000000\nKs 0.000000 0.000000 0.000000\n\n");
		}
		Files.write(objFile.toPath(), obj.toString().getBytes(StandardCharsets.UTF_8));
		Files.write(mtlFile.toPath(), mtl.toString().getBytes(StandardCharsets.UTF_8));
	}

	static Color hslToRgb(short packed) {
		int value = packed & 0xffff;
		double h = ((value >> 10) & 0x3f) / 64.0, s = ((value >> 7) & 7) / 8.0, l = (value & 0x7f) / 128.0;
		if (s <= 0.0001) { int gray = clamp((int)Math.round(l*255.0)); return new Color(gray,gray,gray); }
		double q = l < 0.5 ? l*(1.0+s) : l+s-l*s, p = 2.0*l-q;
		return new Color(clamp((int)Math.round(hueToRgb(p,q,h+1.0/3.0)*255.0)),
				clamp((int)Math.round(hueToRgb(p,q,h)*255.0)),
				clamp((int)Math.round(hueToRgb(p,q,h-1.0/3.0)*255.0)));
	}

	private static double hueToRgb(double p,double q,double t) {
		if (t < 0) t += 1; if (t > 1) t -= 1;
		if (t < 1.0/6.0) return p+(q-p)*6*t;
		if (t < 0.5) return q;
		if (t < 2.0/3.0) return p+(q-p)*(2.0/3.0-t)*6;
		return p;
	}

	private static int clamp(int value) { return Math.max(0, Math.min(255, value)); }

	private static final class Cursor {
		private final byte[] data; private int position;
		Cursor(byte[] data,int position) { this.data=data; this.position=position; }
		int u8() { ensure(1); return data[position++] & 0xff; }
		int s8() { ensure(1); return data[position++]; }
		int u16() { ensure(2); return ((data[position++] & 0xff) << 8) | (data[position++] & 0xff); }
		int smartSigned() { ensure(1); return (data[position] & 0xff) < 128 ? u8()-64 : u16()-49152; }
		void ensure(int amount) { if (position < 0 || position + amount > data.length) throw new IllegalArgumentException("Unexpected end of model data."); }
	}
}
