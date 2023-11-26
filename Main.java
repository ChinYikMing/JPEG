import java.io.*;
import java.util.*;

import javax.imageio.ImageIO;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;

// 8x8 basic block
class Block {
	int[] Y;
	int[] Cb;
	int[] Cr;
	int[][][] rgb;

	Block() {
		this.Y = new int[64];
		this.Cb = new int[64];
		this.Cr = new int[64];
		this.rgb = new int[8][8][3];

		for (int i = 0; i < 8; i++) {
			for (int j = 0; j < 8; j++) {
				for (int k = 0; k < 3; k++) {
					this.rgb[i][j][k] = 0;
				}
			}
		}

		for (int i = 0; i < 64; i++) {
			this.Y[i] = 0;
			this.Cb[i] = 0;
			this.Cr[i] = 0;

			// RGB do not need to be initialized
			// since they will be filled in color space conversion
		}
	}

	public int[][][] getRgb() {
		return rgb;
	}

	public int[] getComponentDataByID(int ID) {
		switch (ID) {
			case 1:
				return this.Y;
			case 2:
				return this.Cb;
			case 3:
				return this.Cr;
			default:
				System.out.println("Invalid component ID: " + ID);
				return null;
		}
	}
}

class JPEGDecoder {
	List<Block> blocks;
	int[] lastDCs;

	JPEGDecoder() {
		this.blocks = new ArrayList<Block>();
		this.lastDCs = new int[3];
		for (int i = 0; i < 3; i++) {
			lastDCs[i] = 0;
		}
	}

	public int[] getLastDCs() {
		return lastDCs;
	}

	public List<Block> decode(JPEGHeader header) {
		BitInputStream bitInputStream = new BitInputStream(header.data);
		int horizontalBlockCount = header.getHorizontalBlockCount();
		int verticalBlockCount = header.getVerticalBlockCount();

		if (header.getHorizontalSamplingFactor() == 2 && header.getWidth() % 2 != 0) {
			horizontalBlockCount += 1;
		}
		if (header.getVerticalSamplingFactor() == 2 && header.getHeight() % 2 != 0) {
			verticalBlockCount += 1;
		}
		for (int i = 0; i < horizontalBlockCount * verticalBlockCount; i++) {
			blocks.add(new Block());
		}

		for (int i = 0; i < header.getVerticalBlockCount(); i += header.getVerticalSamplingFactor()) {
			for (int j = 0; j < header.getHorizontalBlockCount(); j += header.getHorizontalSamplingFactor()) {
				for (int k = 1; k < header.getComponents().size(); k++) {
					int lastDC = getLastDCs()[k - 1];
					Component component = header.getComponents().get(k);
					int componentID = component.getID();
					int vMax = component.getVerticalSamplingFactor();
					int hMax = component.getHorizontalSamplingFactor();

					for (int v = 0; v < vMax; v++) {
						for (int h = 0; h < hMax; h++) {
							int verticalOffset = i + v;
							int horizontalOffset = j + h;
							Block block = blocks
									.get(verticalOffset * horizontalBlockCount + horizontalOffset);
							lastDC = getLastDCs()[k - 1];
							int idx = 0;

							// Decode DC
							int dcLength = huffmanDecode(
									header.getDCHuffmanTable().get(component.getDCHuffmanTableID()),
									bitInputStream);
							if (dcLength > 11) {
								System.out.println("invalid DC coefficient");
								System.exit(1);
							}
							int dcCoeff = bitInputStream.readNBits(dcLength);
							if (dcLength > 0 && dcCoeff < (1 << (dcLength - 1))) {
								dcCoeff = dcCoeff - (1 << dcLength) + 1;
							}
							int finalDCCoeff = dcCoeff + lastDC;
							block.getComponentDataByID(componentID)[header.getIndex2ZigZagMap()
									.get(idx)] = finalDCCoeff;
							getLastDCs()[k - 1] = finalDCCoeff;
							idx++;

							// decode AC
							while (idx < 64) {
								int symbol = huffmanDecode(
										header.getACHuffmanTable().get(component.getACHuffmanTableID()),
										bitInputStream);
								if (symbol == -1) {
									System.out.println("invalid AC coefficent");
									System.exit(1);
								}

								if (symbol == 0x00) { // 63 AC coefficient will be zero
									break;
								}

								int precedingZeroCount = symbol >> 4;
								if (precedingZeroCount == 0xF0) { // it should be 16 zero even 0xF0 is 15
									precedingZeroCount = 16;
								}

								if (idx + precedingZeroCount >= 64) {
									System.out.println("preceding zero count exceeding 8x8 block");
									System.exit(1);
								}

								for (int w = 0; w < precedingZeroCount; w++, idx++) {
									block.getComponentDataByID(componentID)[header.getIndex2ZigZagMap().get(idx)] = 0;
								}

								int acLength = symbol & 0x0F;
								if (acLength > 10) {
									System.out.println("invalid AC coefficient");
									System.exit(1);
								}
								int acCoeff = 0;
								if (acLength != 0) {
									acCoeff = bitInputStream.readNBits(acLength);
									if (acCoeff < (1 << (acLength - 1))) {
										acCoeff = acCoeff - (1 << acLength) + 1;
									}
								}
								block.getComponentDataByID(componentID)[header.getIndex2ZigZagMap().get(idx)] = acCoeff;
								idx++;
							}
						}
					}
				}
			}
		}
		return blocks;
	}

	private int huffmanDecode(HuffmanTable huffmanTable, BitInputStream bitInputStream) {
		int bit;
		int index = 0;
		int symbol = huffmanTable.getSymbol(index);

		while (symbol == Integer.MIN_VALUE) {
			bit = bitInputStream.read();
			if (bit == 0) {
				index = (index << 1) + 1;
			} else {
				index = (index << 1) + 2;
			}

			if (index >= huffmanTable.getTree().length)
				return -1;

			symbol = huffmanTable.getSymbol(index);
		}

		return symbol;
	}

	public List<Block> getBlocks() {
		return blocks;
	}

	public void dequantize(JPEGHeader header) {
		int horizontalBlockCount = header.getHorizontalBlockCount();

		if (header.getHorizontalSamplingFactor() == 2 && header.getWidth() % 2 != 0) {
			horizontalBlockCount = header.getHorizontalBlockCount() + 1;
		}

		for (int i = 0; i < header.getVerticalBlockCount(); i += header.getVerticalSamplingFactor()) {
			for (int j = 0; j < header.getHorizontalBlockCount(); j += header.getHorizontalSamplingFactor()) {
				for (int k = 1; k < header.getComponents().size(); k++) {
					Component component = header.getComponents().get(k);
					int vMax = component.getVerticalSamplingFactor();
					int hMax = component.getHorizontalSamplingFactor();
					int quantizationTableID = component.getQuantizedTableID();

					for (int v = 0; v < vMax; v++) {
						for (int h = 0; h < hMax; h++) {
							int verticalOffset = i + v;
							int horizontalOffset = j + h;
							Block block = blocks
									.get(verticalOffset * horizontalBlockCount + horizontalOffset);
							int[] componentData = block.getComponentDataByID(k);
							QuantizationTable quantizationTable = header.getQuantizationTableByID(quantizationTableID);
							int[] quantizationTableData = quantizationTable.getData();

							for (int l = 0; l < 64; l++) {
								componentData[l] *= quantizationTableData[l];
							}
						}
					}
				}
			}
		}
	}

	final float m0 = (float) 1.847759; // (float)(2.0 * Math.cos(1.0/16.0 * 2.0 * Math.PI));
	final float m1 = (float) 1.4142135; // (float)(2.0 * Math.cos(2.0/16.0 * 2.0 * Math.PI));
	final float m3 = (float) 1.4142135; // (float)(2.0 * Math.cos(2.0/16.0 * 2.0 * Math.PI));
	final float m5 = (float) 0.76536685; // (float)(2.0 * Math.cos(3.0/16.0 * 2.0 * Math.PI));
	final float m2 = (float) 1.0823922; // m0-m5;
	final float m4 = (float) 2.6131258; // m0+m5;

	final float s0 = (float) 0.35355338; // (float)(Math.cos(0.0/16.0 *Math.PI)/Math.sqrt(8));
	final float s1 = (float) 0.49039263; // (float)(Math.cos(1.0/16.0 *Math.PI)/2.0);
	final float s2 = (float) 0.46193975; // (float)(Math.cos(2.0/16.0 *Math.PI)/2.0);
	final float s3 = (float) 0.4157348; // (float)(Math.cos(3.0/16.0 *Math.PI)/2.0);
	final float s4 = (float) 0.35355338; // (float)(Math.cos(4.0/16.0 *Math.PI)/2.0);
	final float s5 = (float) 0.27778512; // (float)(Math.cos(5.0/16.0 *Math.PI)/2.0);
	final float s6 = (float) 0.19134171; // (float)(Math.cos(6.0/16.0 *Math.PI)/2.0);
	final float s7 = (float) 0.09754516; // (float)(Math.cos(7.0/16.0 *Math.PI)/2.0);

	// ref:
	// https://codereview.stackexchange.com/questions/265527/faster-aan-algorithm-for-calculating-discrete-cosine-transform
	public void IDCT8x8(final int[] data) {

		for (int i = 0; i < 8; i++) {
			final float g0 = data[0 * 8 + i] * s0;
			final float g1 = data[4 * 8 + i] * s4;
			final float g2 = data[2 * 8 + i] * s2;
			final float g3 = data[6 * 8 + i] * s6;
			final float g4 = data[5 * 8 + i] * s5;
			final float g5 = data[1 * 8 + i] * s1;
			final float g6 = data[7 * 8 + i] * s7;
			final float g7 = data[3 * 8 + i] * s3;

			final float f0 = g0;
			final float f1 = g1;
			final float f2 = g2;
			final float f3 = g3;
			final float f4 = g4 - g7;
			final float f5 = g5 + g6;
			final float f6 = g5 - g6;
			final float f7 = g4 + g7;

			final float e0 = f0;
			final float e1 = f1;
			final float e2 = f2 - f3;
			final float e3 = f2 + f3;
			final float e4 = f4;
			final float e5 = f5 - f7;
			final float e6 = f6;
			final float e7 = f5 + f7;
			final float e8 = f4 + f6;

			final float d0 = e0;
			final float d1 = e1;
			final float d2 = e2 * m1;
			final float d3 = e3;
			final float d4 = e4 * m2;
			final float d5 = e5 * m3;
			final float d6 = e6 * m4;
			final float d7 = e7;
			final float d8 = e8 * m5;

			final float c0 = d0 + d1;
			final float c1 = d0 - d1;
			final float c2 = d2 - d3;
			final float c3 = d3;
			final float c4 = d4 + d8;
			final float c5 = d5 + d7;
			final float c6 = d6 - d8;
			final float c7 = d7;
			final float c8 = c5 - c6;

			final float b0 = c0 + c3;
			final float b1 = c1 + c2;
			final float b2 = c1 - c2;
			final float b3 = c0 - c3;
			final float b4 = c4 - c8;
			final float b5 = c8;
			final float b6 = c6 - c7;
			final float b7 = c7;

			data[0 * 8 + i] = (int) (b0 + b7);
			data[1 * 8 + i] = (int) (b1 + b6);
			data[2 * 8 + i] = (int) (b2 + b5);
			data[3 * 8 + i] = (int) (b3 + b4);
			data[4 * 8 + i] = (int) (b3 - b4);
			data[5 * 8 + i] = (int) (b2 - b5);
			data[6 * 8 + i] = (int) (b1 - b6);
			data[7 * 8 + i] = (int) (b0 - b7);
		}

		for (int i = 0; i < 8; i++) {
			final float g0 = data[i * 8 + 0] * s0;
			final float g1 = data[i * 8 + 4] * s4;
			final float g2 = data[i * 8 + 2] * s2;
			final float g3 = data[i * 8 + 6] * s6;
			final float g4 = data[i * 8 + 5] * s5;
			final float g5 = data[i * 8 + 1] * s1;
			final float g6 = data[i * 8 + 7] * s7;
			final float g7 = data[i * 8 + 3] * s3;

			final float f0 = g0;
			final float f1 = g1;
			final float f2 = g2;
			final float f3 = g3;
			final float f4 = g4 - g7;
			final float f5 = g5 + g6;
			final float f6 = g5 - g6;
			final float f7 = g4 + g7;

			final float e0 = f0;
			final float e1 = f1;
			final float e2 = f2 - f3;
			final float e3 = f2 + f3;
			final float e4 = f4;
			final float e5 = f5 - f7;
			final float e6 = f6;
			final float e7 = f5 + f7;
			final float e8 = f4 + f6;

			final float d0 = e0;
			final float d1 = e1;
			final float d2 = e2 * m1;
			final float d3 = e3;
			final float d4 = e4 * m2;
			final float d5 = e5 * m3;
			final float d6 = e6 * m4;
			final float d7 = e7;
			final float d8 = e8 * m5;

			final float c0 = d0 + d1;
			final float c1 = d0 - d1;
			final float c2 = d2 - d3;
			final float c3 = d3;
			final float c4 = d4 + d8;
			final float c5 = d5 + d7;
			final float c6 = d6 - d8;
			final float c7 = d7;
			final float c8 = c5 - c6;

			final float b0 = c0 + c3;
			final float b1 = c1 + c2;
			final float b2 = c1 - c2;
			final float b3 = c0 - c3;
			final float b4 = c4 - c8;
			final float b5 = c8;
			final float b6 = c6 - c7;
			final float b7 = c7;

			data[i * 8 + 0] = (int) (b0 + b7) + 128;
			data[i * 8 + 1] = (int) (b1 + b6) + 128;
			data[i * 8 + 2] = (int) (b2 + b5) + 128;
			data[i * 8 + 3] = (int) (b3 + b4) + 128;
			data[i * 8 + 4] = (int) (b3 - b4) + 128;
			data[i * 8 + 5] = (int) (b2 - b5) + 128;
			data[i * 8 + 6] = (int) (b1 - b6) + 128;
			data[i * 8 + 7] = (int) (b0 - b7) + 128;
		}
	}

	public void IDCT(JPEGHeader header) {
		int horizontalBlockCount = header.getHorizontalBlockCount();

		if (header.getHorizontalSamplingFactor() == 2 && header.getWidth() % 2 != 0) {
			horizontalBlockCount = header.getHorizontalBlockCount() + 1;
		}

		for (int i = 0; i < header.getVerticalBlockCount(); i += header.getVerticalSamplingFactor()) {
			for (int j = 0; j < header.getHorizontalBlockCount(); j += header.getHorizontalSamplingFactor()) {
				for (int k = 1; k < header.getComponents().size(); k++) {
					Component component = header.getComponents().get(k);
					int vMax = component.getVerticalSamplingFactor();
					int hMax = component.getHorizontalSamplingFactor();
					for (int v = 0; v < vMax; v++) {
						for (int h = 0; h < hMax; h++) {
							int verticalOffset = i + v;
							int horizontalOffset = j + h;
							Block block = blocks
									.get(verticalOffset * horizontalBlockCount + horizontalOffset);
							int[] componentData = block.getComponentDataByID(k);

							IDCT8x8(componentData);
						}
					}
				}
			}
		}
	}

	public void YCbCr2RGB(JPEGHeader header) {
		int vMax = header.getVerticalSamplingFactor();
		int hMax = header.getHorizontalSamplingFactor();
		int horizontalBlockCount = header.getHorizontalBlockCount();

		if (header.getHorizontalSamplingFactor() == 2 && header.getWidth() % 2 != 0) {
			horizontalBlockCount = header.getHorizontalBlockCount() + 1;
		}

		for (int i = 0; i < header.getVerticalBlockCount(); i += header.getVerticalSamplingFactor()) {
			for (int j = 0; j < header.getHorizontalBlockCount(); j += header.getHorizontalSamplingFactor()) {
				Block chromaBlock = blocks.get(i * horizontalBlockCount + j);
				int[] cbData = chromaBlock.getComponentDataByID(2);
				int[] crData = chromaBlock.getComponentDataByID(3);

				int[][][] yDataRgb = null;
				for (int v = 0; v < vMax; v++) {
					for (int h = 0; h < hMax; h++) {
						int verticalOffset = i + v;
						int horizontalOffset = j + h;
						Block yBlock = blocks
								.get(verticalOffset * horizontalBlockCount + horizontalOffset);
						int[] yData = yBlock.getComponentDataByID(1);

						for (int w = 0; w < 8; w++) {
							for (int z = 0; z < 8; z++) {
								int chromaPixelIndex = (w / vMax + 4 * v) * 8 + (z / hMax + 4 * h);
								int yPixelIndex = w * 8 + z;

								int r = (int) (yData[yPixelIndex] + 1.402 * (crData[chromaPixelIndex] - 128));
								int g = (int) (yData[yPixelIndex] - 0.34414 * (cbData[chromaPixelIndex] - 128)
										- 0.71414 * (crData[chromaPixelIndex] - 128));
								int b = (int) (yData[yPixelIndex] + 1.772 * (cbData[chromaPixelIndex] - 128));

								// prevent out of range [0 - 255]
								r = Math.max(0, Math.min(255, r));
								g = Math.max(0, Math.min(255, g));
								b = Math.max(0, Math.min(255, b));

								yDataRgb = yBlock.getRgb();
								yDataRgb[w][yPixelIndex % 8][0] = r;
								yDataRgb[w][yPixelIndex % 8][1] = g;
								yDataRgb[w][yPixelIndex % 8][2] = b;
							}
						}
					}
				}
			}
		}
	}
}

class BitInputStream {
	List<Integer> data;
	int byteIndex;
	int bitIndex;

	BitInputStream(List<Integer> data) {
		this.data = data;
		this.byteIndex = 0;
		this.bitIndex = 7;
	}

	public int read() {
		if (byteIndex >= data.size()) {
			System.out.println("Invalid bit");
			return -1;
		}

		int bit = (data.get(byteIndex) >> bitIndex) & 0x1;
		bitIndex -= 1;
		if (bitIndex == -1) {
			bitIndex = 7;
			byteIndex += 1;
		}

		return bit;
	}

	public int readNBits(int n) {
		int result = 0;
		for (int i = 0; i < n; i++) {
			int bit = read();
			if (bit == -1) {
				System.out.println("Invalid bit");
				return -1;
			}
			result <<= 1;
			result |= bit;
		}
		return result;
	}
}

class HuffmanTable {
	int ID;
	HashMap<Integer, List<Integer>> bitSymbolTable;
	int[] tree; // used to represent the huffman tree

	HuffmanTable(int ID) {
		this.ID = ID;
		this.bitSymbolTable = new HashMap<Integer, List<Integer>>();
		for (int i = 0; i < 16; i++) {
			this.bitSymbolTable.put(i + 1, new ArrayList<Integer>()); // i + 1 simply starts from index 1
		}
		this.tree = new int[131072];
		for (int i = 0; i < this.tree.length; i++) {
			this.tree[i] = Integer.MIN_VALUE; // Integer.MIN_VALUE means not reach leaf node
		}
	}

	public int getID() {
		return ID;
	}

	public HashMap<Integer, List<Integer>> getBitSymbolTable() {
		return bitSymbolTable;
	}

	public int[] getTree() {
		return tree;
	}

	// build the huffman tree
	// left child index: 2i + 1
	// right child index: 2i + 2
	/*
	 * root(0)
	 * / \
	 * left(1) right(2)
	 */
	public void buildTree() {
		int leftMostIdx = 1; // the first left most index should be the left child of root node
		int size;
		for (int i = 0; i < this.bitSymbolTable.size(); i++) {
			size = this.bitSymbolTable.get(i + 1).size();
			if (size == 0) {
				leftMostIdx = (leftMostIdx << 1) + 1; // add a new level
				continue;
			}

			for (int j = 0; j < size; j++) {
				this.tree[leftMostIdx] = this.bitSymbolTable.get(i + 1).get(j);
				leftMostIdx++;
			}

			leftMostIdx = (leftMostIdx << 1) + 1; // add a new level
		}
	}

	public int getSymbol(int index) {
		return tree[index];
	}
}

class Component {
	int ID;
	int quantizedTableID;
	int DCHuffmanTableID;
	int ACHuffmanTableID;
	int horizontalSamplingFactor;
	int verticalSamplingFactor;

	Component() {

	}

	public int getHorizontalSamplingFactor() {
		return horizontalSamplingFactor;
	}

	public int getVerticalSamplingFactor() {
		return verticalSamplingFactor;
	}

	public int getID() {
		return ID;
	}

	public int getACHuffmanTableID() {
		return ACHuffmanTableID;
	}

	public int getDCHuffmanTableID() {
		return DCHuffmanTableID;
	}

	public int getQuantizedTableID() {
		return quantizedTableID;
	}

	public void setHorizontalSamplingFactor(int horizontalSamplingFactor) {
		this.horizontalSamplingFactor = horizontalSamplingFactor;
	}

	public void setVerticalSamplingFactor(int verticalSamplingFactor) {
		this.verticalSamplingFactor = verticalSamplingFactor;
	}

	public void setID(int iD) {
		ID = iD;
	}

	public void setACHuffmanTableID(int aCHuffmanTableID) {
		ACHuffmanTableID = aCHuffmanTableID;
	}

	public void setDCHuffmanTableID(int dCHuffmanTableID) {
		DCHuffmanTableID = dCHuffmanTableID;
	}

	public void setQuantizedTableID(int quantizedTableID) {
		this.quantizedTableID = quantizedTableID;
	}
}

class QuantizationTable {
	int[] data;
	int ID;

	QuantizationTable(int ID) {
		this.data = new int[64];
		this.ID = ID;
	}

	public int[] getData() {
		return data;
	}

	public int getID() {
		return ID;
	}

	public void setID(int iD) {
		ID = iD;
	}
}

class JPEGHeader {
	int width;
	int height;
	List<Component> components;
	List<Integer> data;
	List<QuantizationTable> quantizationTable;
	List<HuffmanTable> DCHuffmanTable;
	List<HuffmanTable> ACHuffmanTable;
	HashMap<Integer, Integer> index2ZigZagMap;

	int verticalSamplingFactor = 1; // 4:4:4
	int horizontalSamplingFactor = 1; // 4:4:4
	int verticalBlockCount;
	int horizontalBlockCount;
	boolean isVerticalMultipleBlock; // true: vertical is multiple of 8x8 block
	boolean isHorizontalMultipleBlock; // true: horizontal multiple of 8x8 block

	// progessive stuff
	int startOfSpectralSelection;
	int endOfSpectralSelection;
	int succApprox;

	JPEGHeader() {
		this.quantizationTable = new ArrayList<QuantizationTable>();
		this.DCHuffmanTable = new ArrayList<HuffmanTable>();
		this.ACHuffmanTable = new ArrayList<HuffmanTable>();
		this.data = new ArrayList<Integer>();
		this.components = new ArrayList<Component>();
		this.components.add(null); // since the component ID starts from 1 so padding index 0 with null

		this.startOfSpectralSelection = 0;
		this.endOfSpectralSelection = 0;
		this.succApprox = 0;

		this.index2ZigZagMap = new HashMap<Integer, Integer>();
		this.index2ZigZagMap.put(0, 0);
		this.index2ZigZagMap.put(1, 1);
		this.index2ZigZagMap.put(2, 8);
		this.index2ZigZagMap.put(3, 16);
		this.index2ZigZagMap.put(4, 9);
		this.index2ZigZagMap.put(5, 2);
		this.index2ZigZagMap.put(6, 3);
		this.index2ZigZagMap.put(7, 10);
		this.index2ZigZagMap.put(8, 17);
		this.index2ZigZagMap.put(9, 24);
		this.index2ZigZagMap.put(10, 32);
		this.index2ZigZagMap.put(11, 25);
		this.index2ZigZagMap.put(12, 18);
		this.index2ZigZagMap.put(13, 11);
		this.index2ZigZagMap.put(14, 4);
		this.index2ZigZagMap.put(15, 5);
		this.index2ZigZagMap.put(16, 12);
		this.index2ZigZagMap.put(17, 19);
		this.index2ZigZagMap.put(18, 26);
		this.index2ZigZagMap.put(19, 33);
		this.index2ZigZagMap.put(20, 40);
		this.index2ZigZagMap.put(21, 48);
		this.index2ZigZagMap.put(22, 41);
		this.index2ZigZagMap.put(23, 34);
		this.index2ZigZagMap.put(24, 27);
		this.index2ZigZagMap.put(25, 20);
		this.index2ZigZagMap.put(26, 13);
		this.index2ZigZagMap.put(27, 6);
		this.index2ZigZagMap.put(28, 7);
		this.index2ZigZagMap.put(29, 14);
		this.index2ZigZagMap.put(30, 21);
		this.index2ZigZagMap.put(31, 28);
		this.index2ZigZagMap.put(32, 35);
		this.index2ZigZagMap.put(33, 42);
		this.index2ZigZagMap.put(34, 49);
		this.index2ZigZagMap.put(35, 56);
		this.index2ZigZagMap.put(36, 57);
		this.index2ZigZagMap.put(37, 50);
		this.index2ZigZagMap.put(38, 43);
		this.index2ZigZagMap.put(39, 36);
		this.index2ZigZagMap.put(40, 29);
		this.index2ZigZagMap.put(41, 22);
		this.index2ZigZagMap.put(42, 15);
		this.index2ZigZagMap.put(43, 23);
		this.index2ZigZagMap.put(44, 30);
		this.index2ZigZagMap.put(45, 37);
		this.index2ZigZagMap.put(46, 44);
		this.index2ZigZagMap.put(47, 51);
		this.index2ZigZagMap.put(48, 58);
		this.index2ZigZagMap.put(49, 59);
		this.index2ZigZagMap.put(50, 52);
		this.index2ZigZagMap.put(51, 45);
		this.index2ZigZagMap.put(52, 38);
		this.index2ZigZagMap.put(53, 31);
		this.index2ZigZagMap.put(54, 39);
		this.index2ZigZagMap.put(55, 46);
		this.index2ZigZagMap.put(56, 53);
		this.index2ZigZagMap.put(57, 60);
		this.index2ZigZagMap.put(58, 61);
		this.index2ZigZagMap.put(59, 54);
		this.index2ZigZagMap.put(60, 47);
		this.index2ZigZagMap.put(61, 55);
		this.index2ZigZagMap.put(62, 62);
		this.index2ZigZagMap.put(63, 63);
	}

	public void pushData(int data){
		this.data.add(data);
	}

	public void clearData(){
		this.data.clear();
	}

	public int getStartOfSpectralSelection() {
		return startOfSpectralSelection;
	}

	public void setStartOfSpectralSelection(int startOfSpectralSelection) {
		this.startOfSpectralSelection = startOfSpectralSelection;
	}

	public int getEndOfSpectralSelection() {
		return endOfSpectralSelection;
	}

	public void setEndOfSpectralSelection(int endOfSpectralSelection) {
		this.endOfSpectralSelection = endOfSpectralSelection;
	}

	public int getSuccApprox() {
		return succApprox;
	}

	public void setSuccApprox(int succApprox) {
		this.succApprox = succApprox;
	}


	public HashMap<Integer, Integer> getIndex2ZigZagMap() {
		return index2ZigZagMap;
	}

	boolean getIsVerticalMultipleBlock() {
		return isVerticalMultipleBlock;
	}

	boolean getIsHorizontalMultipleBlock() {
		return isHorizontalMultipleBlock;
	}

	public void setHorizontalMultipleBlock(boolean isHorizontalMultipleBlock) {
		this.isHorizontalMultipleBlock = isHorizontalMultipleBlock;
	}

	public void setVerticalMultipleBlock(boolean isVerticalMultipleBlock) {
		this.isVerticalMultipleBlock = isVerticalMultipleBlock;
	}

	public int getVerticalBlockCount() {
		return verticalBlockCount;
	}

	public int getHorizontalBlockCount() {
		return horizontalBlockCount;
	}

	public int getVerticalSamplingFactor() {
		return verticalSamplingFactor;
	}

	public int getHorizontalSamplingFactor() {
		return horizontalSamplingFactor;
	}

	public List<Component> getComponents() {
		return components;
	}

	public QuantizationTable getQuantizationTableByID(int ID) {
		return quantizationTable.get(ID);
	}

	public List<QuantizationTable> getQuantizationTable() {
		return quantizationTable;
	}

	public List<HuffmanTable> getACHuffmanTable() {
		return ACHuffmanTable;
	}

	public List<HuffmanTable> getDCHuffmanTable() {
		return DCHuffmanTable;
	}

	public List<Integer> getData() {
		return data;
	}

	public int getHeight() {
		return height;
	}

	public void setHeight(int height) {
		this.height = height;
	}

	public int getWidth() {
		return width;
	}

	public void setWidth(int width) {
		this.width = width;
	}
}

class Main {
	public static void main(String[] args) {
		if (args.length != 1) {
			usage();
			System.exit(1);
		}

		String jpegImageFilename = args[0];
		JPEGHeader jpegHeader = new JPEGHeader();
		JPEGDecoder jpegDecoder = new JPEGDecoder();
		try {
			File jpegImage = new File(jpegImageFilename);
			InputStream jpegByteStream = new FileInputStream(jpegImage);

			parseJPEGHeaderAndDecode(jpegByteStream, jpegHeader, jpegDecoder);
			jpegByteStream.close();
			// System.exit(0);

			// once decode for baseline mode, and last decode for progessive mode
			jpegDecoder.decode(jpegHeader); 

			jpegDecoder.dequantize(jpegHeader);
			jpegDecoder.IDCT(jpegHeader);
			jpegDecoder.YCbCr2RGB(jpegHeader);
			saveBMP(jpegImageFilename, jpegHeader, jpegDecoder.getBlocks());
		} catch (Exception e) {
			System.out.println("Some error occur");
			e.printStackTrace();
		}
	}

	public static boolean reachMarker(int byteData) {
		return byteData == 0xFF;
	}

	public static int mergeTwoBytes(int byteData1, int byteData2) {
		return (byteData1 << 8) | byteData2;
	}

	public static void usage() {
		System.out.println("Usage: java Main jpeg_filename");
	}

	public static void parseSOS(InputStream jpegByteStream, JPEGHeader jpegHeader) {
		int byteData, byteData1, byteData2;
		int markerSize;

		try {
			byteData1 = jpegByteStream.read();
			byteData2 = jpegByteStream.read();
			markerSize = mergeTwoBytes(byteData1, byteData2) - 2;

			int componentCount = jpegByteStream.read();
			for (int i = 0; i < componentCount; i++) {
				int componentID = jpegByteStream.read();
				byteData = jpegByteStream.read();
				int DCHuffmanTableID = (byteData & 0xF0) >> 4;
				int ACHuffmanTableID = byteData & 0x0F;

				jpegHeader.getComponents().get(componentID).setACHuffmanTableID(ACHuffmanTableID);
				jpegHeader.getComponents().get(componentID).setDCHuffmanTableID(DCHuffmanTableID);
			}
			markerSize -= (1 + componentCount * 2);
			int startOfSpectralSelection = jpegByteStream.read();
			jpegHeader.setStartOfSpectralSelection(startOfSpectralSelection);
			int endOfSpectralSelection = jpegByteStream.read();
			jpegHeader.setEndOfSpectralSelection(endOfSpectralSelection);
			int succApprox = jpegByteStream.read();
			jpegHeader.setSuccApprox(succApprox);

			System.out.println("startOfSpectralSelection: " + startOfSpectralSelection);
			System.out.println("endOfSpectralSelection: " + endOfSpectralSelection);
			System.out.println("succApprox: " + succApprox);

		} catch (Exception e) {
			e.printStackTrace();
			System.out.println("Some error occur during parsing SOS");
			System.exit(1);
		}
	}

	public static void parseDHT(InputStream jpegByteStream, JPEGHeader jpegHeader) {
		int byteData, byteData1, byteData2;
		int markerSize;

		try {
			byteData1 = jpegByteStream.read();
			byteData2 = jpegByteStream.read();
			markerSize = mergeTwoBytes(byteData1, byteData2) - 2;

			while (markerSize > 0) {
				int tableInfo = jpegByteStream.read();
				markerSize--;

				int isAC = tableInfo & 0x10;
				int ID = tableInfo & 0x01;
				HuffmanTable huffmanTable = new HuffmanTable(ID);
				HashMap<Integer, List<Integer>> bitSymbolTable = huffmanTable.getBitSymbolTable();
				byte[] bytes = jpegByteStream.readNBytes(16);
				markerSize -= 16;

				for (int i = 0; i < 16; i++) {
					int bitLength = (int) bytes[i];
					if (bitLength == 0) {
						continue;
					}

					for (int j = 0; j < bitLength; j++) {
						byteData = jpegByteStream.read();
						bitSymbolTable.get(i + 1).add(byteData); // i + 1 simply starts from index 1
						markerSize--;
					}
				}

				// TODO: can be build when building the bitSymbolTable
				huffmanTable.buildTree();

				if (isAC == 0x10) {
					jpegHeader.getACHuffmanTable().add(huffmanTable);
				} else {
					jpegHeader.getDCHuffmanTable().add(huffmanTable);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println("Some error occur when parsing DHT");
			System.exit(0);
		}
	}

	public static void parseSOF(InputStream jpegByteStream, JPEGHeader jpegHeader) {
		int byteData, byteData1, byteData2;
		int markerSize;
		int height;
		int width;
		try {
			byteData1 = jpegByteStream.read();
			byteData2 = jpegByteStream.read();
			markerSize = mergeTwoBytes(byteData1, byteData2) - 2;
			jpegByteStream.read(); // precision
			byteData1 = jpegByteStream.read();
			byteData2 = jpegByteStream.read();
			height = mergeTwoBytes(byteData1, byteData2);
			byteData1 = jpegByteStream.read();
			byteData2 = jpegByteStream.read();
			width = mergeTwoBytes(byteData1, byteData2);

			jpegHeader.setHeight(height);
			jpegHeader.setWidth(width);

			int modRes = width % 8;
			int divRes = width / 8;
			if (modRes != 0) {
				jpegHeader.horizontalBlockCount = divRes + 1;
				jpegHeader.setHorizontalMultipleBlock(false);
			} else {
				jpegHeader.horizontalBlockCount = divRes;
				jpegHeader.setHorizontalMultipleBlock(true);
			}

			modRes = height % 8;
			divRes = height / 8;
			if (modRes != 0) {
				jpegHeader.verticalBlockCount = divRes + 1;
				jpegHeader.setVerticalMultipleBlock(false);
			} else {
				jpegHeader.verticalBlockCount = divRes;
				jpegHeader.setVerticalMultipleBlock(true);
			}

			int componentCount = jpegByteStream.read();
			for (int i = 0; i < componentCount; i++) {
				Component component = new Component();
				int componentID = jpegByteStream.read();
				byteData = jpegByteStream.read();
				int horizontalSamplingFactor = (byteData & 0xF0) >> 4;
				int verticalSamplingFactor = byteData & 0x0F;
				int quantizeID = jpegByteStream.read();

				component.setID(componentID);
				component.setHorizontalSamplingFactor(horizontalSamplingFactor);
				component.setVerticalSamplingFactor(verticalSamplingFactor);
				component.setQuantizedTableID(quantizeID);

				if (componentID == 1) {
					// the largest sampling factors since Luminance component cannot be subsamspling
					jpegHeader.horizontalSamplingFactor = horizontalSamplingFactor;
					jpegHeader.verticalSamplingFactor = verticalSamplingFactor;
				}

				jpegHeader.getComponents().add(component);
			}
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println("Some error occur");
			System.exit(1);
		}

	}

	public static void parseJPEGHeaderAndDecode(InputStream jpegByteStream, JPEGHeader jpegHeader, JPEGDecoder jpegDecoder) {
		int byteData, byteData1, byteData2;
		int markerSize;

		try {
			while (jpegByteStream.available() > 0) {
				byteData = jpegByteStream.read();

				if (!reachMarker(byteData))
					continue;

				byteData = jpegByteStream.read();
				switch (byteData) {
					// SOI(Start of Image)
					case 0xD8:
						jpegByteStream.read();
						break;

					// APP0
					case 0xE0:
						byteData1 = jpegByteStream.read();
						byteData2 = jpegByteStream.read();
						markerSize = mergeTwoBytes(byteData1, byteData2) - 2;
						jpegByteStream.readNBytes(markerSize);
						break;

					// DQT(Define Quantization Table)
					case 0xDB:
						// marker size
						byteData1 = jpegByteStream.read();
						byteData2 = jpegByteStream.read();
						markerSize = mergeTwoBytes(byteData1, byteData2) - 2;

						while (markerSize > 0) {
							byteData = jpegByteStream.read();
							markerSize--;

							int is16Bit = (byteData & 0xF0) >> 4;
							int tableID = byteData & 0x0F;
							QuantizationTable quantizationTable = new QuantizationTable(tableID);

							if (is16Bit == 0x1) {
								for (int i = 0; i < 64; i++) {
									byteData1 = jpegByteStream.read();
									byteData2 = jpegByteStream.read();
									markerSize -= 2;

									quantizationTable.getData()[jpegHeader.getIndex2ZigZagMap()
											.get(i)] = (byteData1 << 8) | byteData2;
								}
							} else { // 8 bit
								for (int i = 0; i < 64; i++) {
									byteData = jpegByteStream.read();
									markerSize--;

									quantizationTable.getData()[jpegHeader.getIndex2ZigZagMap()
											.get(i)] = byteData;
								}
							}
							jpegHeader.getQuantizationTable().add(quantizationTable);
						}
						break;

					// DHT(Define Huffman Table)
					case 0xC4:
						parseDHT(jpegByteStream, jpegHeader);
						break;

					// SOF(Start of Frame) baseline
					case 0xC0:
						parseSOF(jpegByteStream, jpegHeader);
						break;

					// SOF(Start of Frame) progessive
					case 0xC2:
						parseSOF(jpegByteStream, jpegHeader);
						break;

					// SOS(Start of Scan)
					case 0xDA:
						parseSOS(jpegByteStream, jpegHeader);

						byteData1 = jpegByteStream.read();
						while (jpegByteStream.available() > 0) {
							byteData2 = byteData1;
							byteData1 = jpegByteStream.read();
							if (reachMarker(byteData2)) {
								// EOI(End of Image)
								if (byteData1 == 0xD9) {
									break;
								} else if (byteData1 == 0xFF) { // skip multiple consecutive 0xFF
									continue;
								} else if (byteData1 == 0xC4) { // DHT
									parseDHT(jpegByteStream, jpegHeader);
									jpegDecoder.decode(jpegHeader);
									jpegHeader.clearData();
								} else if (byteData1 == 0xDA) { // SOS again
									parseSOS(jpegByteStream, jpegHeader);
									parseDHT(jpegByteStream, jpegHeader);
									jpegDecoder.decode(jpegHeader);
									jpegHeader.clearData();
								} else if (byteData1 == 0x00) { // ignore zero after 0xFF
									jpegHeader.pushData(byteData2);
									byteData1 = jpegByteStream.read();
								} else {
									System.out.println("SOS: invalid marker");
								}
							} else {
								jpegHeader.pushData(byteData2);
							}
						}
						break;

					default:
						break;
				}
			}
		} catch (Exception e) {
			System.out.println("Some error occur");
			e.printStackTrace();
		}
	}

	public static void saveBMP(String filename, JPEGHeader header, List<Block> blocks) {
		int blockIndex = 0;
		int prevBlockIndex = 0;
		int bufIdx = 0;
		BufferedImage bufferedImage = new BufferedImage(header.getWidth(), header.getHeight(),
				BufferedImage.TYPE_3BYTE_BGR);
		byte[] bufferedImageBytes = ((DataBufferByte) bufferedImage.getRaster().getDataBuffer()).getData();

		int verticalBlockCountReal = header.getHeight() / 8;
		int horizontalBlockCountReal = header.getWidth() / 8;
		int horizontalPixelPadding = 0;

		if (!header.getIsHorizontalMultipleBlock()) {
			horizontalPixelPadding = header.getWidth() % 8;
		}

		for (int i = 0; i < verticalBlockCountReal; i++) {
			for (int k = 0; k < 8; k++) { // row
				blockIndex = prevBlockIndex;
				for (int j = 0; j < horizontalBlockCountReal; j++) {
					Block block = blocks.get(blockIndex);
					int rgb_[][][] = block.getRgb();
					for (int l = 0; l < 8; l++) { // col
						for (int o = 2; o >= 0; o--) { // channel
							bufferedImageBytes[bufIdx++] = (byte) rgb_[k][l][o];
						}
					}
					blockIndex++;
				}

				// horizontal padding on each row
				if (horizontalPixelPadding != 0) {
					Block block = blocks.get(blockIndex);
					int rgb_[][][] = block.getRgb();
					for (int l = 0; l < horizontalPixelPadding; l++) { // col
						for (int o = 2; o >= 0; o--) { // channel
							bufferedImageBytes[bufIdx++] = (byte) rgb_[k][l][o];
						}
					}
					blockIndex++;
				}
			}
			prevBlockIndex = blockIndex;
		}

		// vertical padding
		int remainingPixelBytes = (header.getWidth() * header.getHeight() * 3) - bufIdx;
		for (int i = 0; i < remainingPixelBytes; i++) {
			bufferedImageBytes[bufIdx++] = (byte) 0x00;
		}

		try {
			int dotIndex = filename.lastIndexOf('.');
			String basename = (dotIndex == -1) ? filename : filename.substring(0, dotIndex);
			File outputBMP = new File(basename + ".bmp");
			ImageIO.write(bufferedImage, "bmp", outputBMP);
		} catch (Exception e) {
			System.out.println("Some error occur");
			e.printStackTrace();
		}
	}
}