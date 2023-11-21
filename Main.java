import java.io.*;
import java.util.*;

import javax.imageio.ImageIO;

import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferInt;
import java.awt.image.BufferedImage;
import java.awt.image.SinglePixelPackedSampleModel;
import java.awt.Point;
import java.awt.image.DataBufferByte;

import javax.media.jai.operator.IDCTDescriptor;
import javax.media.jai.RenderedOp;

// 8x8 basic block
class Block {
	int[] Y;
	int[] Cb;
	int[] Cr;
	int[] R;
	int[] G;
	int[] B;

	Block() {
		this.Y = new int[64];
		this.Cb = new int[64];
		this.Cr = new int[64];
		this.R = new int[64];
		this.G = new int[64];
		this.B = new int[64];

		for (int i = 0; i < 64; i++) {
			this.Y[i] = 0;
			this.Cb[i] = 0;
			this.Cr[i] = 0;

			// RGB do not need to be initialized
			// since they will be filled in color space conversion
		}
	}

	public int[] getComponentDataByID(int ID) {
		switch (ID) {
			case 1:
				return this.Y;
			case 2:
				return this.Cb;
			case 3:
				return this.Cr;
			case 4:
				return this.R;
			case 5:
				return this.G;
			case 6:
				return this.B;
			default:
				System.out.println("Invalid component ID: " + ID);
				return null;
		}
	}
}

class JPEGDecoder {
	List<Block> blocks;

	JPEGDecoder() {
		this.blocks = new ArrayList<Block>();
	}

	public List<Block> decode(JPEGHeader header) {
		BitInputStream bitInputStream = new BitInputStream(header.data);

		// TODO: padding with not exact 8x8
		int blockCount = (header.getWidth() * header.getHeight()) / (8 * 8);
		for (int bc = 0; bc < blockCount; bc++) {
			Block block = new Block();
			for (int i = 1; i < header.getComponents().size(); i++) {
				int idx = 0;
				Component component = header.getComponents().get(i);
				int componentID = component.getID();
				// int vMax = component.getVerticalSamplingFactor();
				// int hMax = component.getHorizontalSamplingFactor();

				// decode DC
				// (DC length, DC coefficient)
				// System.out.println("first byte: " + String.format("0x%X",
				// header.getData().get(0)));
				// System.out.println("second byte: " + String.format("0x%X",
				// header.getData().get(1)));
				// System.exit(1);

				// System.out.println("DC table ID: " + component.getDCHuffmanTableID());
				// System.out.println("AC table ID: " + component.getACHuffmanTableID());
				// System.out.println("huffman coded byte: ");
				// for (int k = 0; k < 128; k++) {
				// if (k % 16 == 0)
				// System.out.println();
				// int ret =
				// huffmanDecode(header.getACHuffmanTable().get(component.getACHuffmanTableID()),
				// bitInputStream);
				// System.out.print(String.format("0x%x", (int) ret) + " ");
				// System.out.print(String.format("0x%x", (int) bitInputStream.readNBits(8)) + "
				// ");
				// }
				// System.exit(1);

				// System.out.println("DC table ID: " + component.getDCHuffmanTableID());
				// System.out.println("AC table ID: " + component.getACHuffmanTableID());
				// System.out.println("Decoding DC value...");
				int dcLength = huffmanDecode(header.getDCHuffmanTable().get(component.getDCHuffmanTableID()),
						bitInputStream);
				if (dcLength > 11) {
					System.out.println("Error: Invalid DC coefficent");
					System.exit(1);
				}
				int dcCoeff = bitInputStream.readNBits(dcLength);
				if (dcLength > 0 && dcCoeff < (1 << (dcLength - 1))) {
					dcCoeff = dcCoeff - (1 << dcLength) + 1;
				}
				block.getComponentDataByID(componentID)[header.getIndex2ZigZagMap().get(idx)] = dcCoeff;
				idx++;
				// System.out.println();
				// System.out.println("dc length: " + dcLength + ", dcCoeff: " + dcCoeff);
				// System.out.println("DC coefficent: " + dcCoeff);
				// System.out.println("Done decoding DC value...");
				// System.out.println("dclength: " + dcLength + ", dcCoeff: " + dcCoeff);

				// for(int k = 0; k < 57; k++){
				// System.out.print(bitInputStream.read());
				// }
				// System.out.println();
				// System.exit(1);

				// decode AC
				// (preceding zero count, AC coefficient bit length)
				// System.out.println("Decoding AC value...");
				while (idx < 64) {
					// System.out.println();
					// System.out.print("bit: ");
					int symbol = huffmanDecode(header.getACHuffmanTable().get(component.getACHuffmanTableID()),
							bitInputStream);
					if (symbol == -1) {
						System.out.println("Error: invalid AC coefficent");
						System.exit(1);
					}
					// System.out.println("symbol: " + String.format("0x%X", symbol));

					if (symbol == 0x00) { // 63 AC coefficient will be zero
						// zero is filled during initialization, so skip it
						// for (; idx < 64; idx++) {
						// block.getData()[index2ZigZagMap.get(idx)] = 0;
						// }

						break;
					}

					int precedingZeroCount = symbol >> 4;
					if (precedingZeroCount == 0xF0) { // it should be 16 zero even 0xF0 is 15
						precedingZeroCount = 16;
					}

					if (idx + precedingZeroCount >= 64) {
						System.out.println(
								"idx: " + idx + ", precedingZeroCount: " + String.format("0x%X", precedingZeroCount));
						System.out.println("Error: preceding zero count exceeding 8x8 block");
						System.exit(1);
					}

					for (int j = 0; j < precedingZeroCount; j++, idx++) {
						block.getComponentDataByID(componentID)[header.getIndex2ZigZagMap().get(idx)] = 0;
					}

					int acLength = symbol & 0x0F;
					if (acLength > 10) {
						System.out.println("Error: invalid AC coefficent");
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
					// System.out.println("AC coefficent: " + acCoeff);
				}
				// System.out.println("Done decoding AC value...");
			}
			blocks.add(block);
		}

		return blocks;
	}

	private int huffmanDecode(HuffmanTable huffmanTable, BitInputStream bitInputStream) {
		int bit;
		int index = 0;
		int symbol = huffmanTable.getSymbol(index);

		// System.out.print("bit: ");
		while (symbol == Integer.MIN_VALUE) {
			bit = bitInputStream.read();
			if (bit == 0) {
				index = (index << 1) + 1;
			} else {
				index = (index << 1) + 2;
			}
			// System.out.print("" + bit);

			if (index >= huffmanTable.getTree().length)
				return -1;

			symbol = huffmanTable.getSymbol(index);
		}
		// System.out.println();

		return symbol;
	}

	public List<Block> getBlocks() {
		return blocks;
	}

	public void dequantize(JPEGHeader header) {
		for (int i = 0; i < blocks.size(); i++) {
			Block block = blocks.get(i);

			for (int j = 1; j < header.getComponents().size(); j++) {
				Component component = header.getComponents().get(j);
				int[] componentData = block.getComponentDataByID(j);
				QuantizationTable quantizationTable = header.getQuantizationTableByID(component.getQuantizedTableID());
				int[] quantizationTableData = quantizationTable.getData();

				for (int k = 0; k < 64; k++) {
					componentData[k] *= quantizationTableData[k];
				}
			}
		}
	}

	public void IDCT(JPEGHeader header) {
		int componentCount = header.getComponents().size() - 1;
		BufferedImage bufferedImage = new BufferedImage(8, 8, BufferedImage.TYPE_BYTE_GRAY);
		DataBuffer dataBuffer;
		int[] bitMasks = { 0xFF };
		WritableRaster writableRaster;
		RenderedOp renderedOp;
		double[] IDCTResult = new double[64];

		for (int i = 0; i < blocks.size(); i++) {
			Block block = blocks.get(i);
			for (int j = 1; j <= componentCount; j++) {
				int[] data = block.getComponentDataByID(j);
				dataBuffer = new DataBufferInt(data, data.length);
				writableRaster = Raster.createWritableRaster(
						new SinglePixelPackedSampleModel(DataBuffer.TYPE_INT, 8, 8, bitMasks),
						dataBuffer,
						new Point(0, 0));

				bufferedImage.setData(writableRaster);
				renderedOp = IDCTDescriptor.create(bufferedImage, null);
				renderedOp.getData().getPixels(0, 0, 8, 8, IDCTResult);

				for (int k = 0; k < 64; k++) {
					block.getComponentDataByID(j)[k] = (int) Math.round(IDCTResult[k]);
				}

				// for(int k = 0; k < 64; k++){
				// if(k % 8 == 0){
				// System.out.println();
				// }
				// System.out.print(block.getComponentDataByID(j)[k] + ", ");
				// }
			}
		}
	}

	public void YCbCr2RGB() {
		for (int i = 0; i < blocks.size(); i += 3) {
			Block Y = blocks.get(i);
			Block Cb = blocks.get(i + 1);
			Block Cr = blocks.get(i + 2);

			int[] YData = Y.getComponentDataByID(1);
			int[] CbData = Cb.getComponentDataByID(2);
			int[] CrData = Cr.getComponentDataByID(3);

			for (int j = 0; j < 64; j++) {
				int r = (int) (YData[j] + 1.402 * (CrData[j] - 128));
				int g = (int) (YData[j] - 0.34414 * (CbData[j] - 128) - 0.71414 * (CrData[j] - 128));
				int b = (int) (YData[j] + 1.772 * (CbData[j] - 128));

				if (r < 0)
					r = 0;
				else if (r > 255)
					r = 255;
				if (g < 0)
					g = 0;
				else if (g > 255)
					g = 255;
				if (b < 0)
					b = 0;
				else if (b > 255)
					b = 255;

				Y.getComponentDataByID(4)[j] = r;
				Y.getComponentDataByID(5)[j] = g;
				Y.getComponentDataByID(6)[j] = b;
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

		// System.out.print(bit);

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
	byte[] sos;
	List<Component> components;
	List<QuantizationTable> quantizationTable;
	List<HuffmanTable> DCHuffmanTable;
	List<HuffmanTable> ACHuffmanTable;
	List<Integer> data;
	HashMap<Integer, Integer> index2ZigZagMap;

	JPEGHeader() {
		this.sos = new byte[10];
		this.quantizationTable = new ArrayList<QuantizationTable>();
		this.DCHuffmanTable = new ArrayList<HuffmanTable>();
		this.ACHuffmanTable = new ArrayList<HuffmanTable>();
		this.data = new ArrayList<Integer>();
		this.components = new ArrayList<Component>();
		this.components.add(null); // since the component ID starts from 1 so padding index 0 with null

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

	public void pushData(int value) {
		data.add(value);
	}

	public HashMap<Integer, Integer> getIndex2ZigZagMap() {
		return index2ZigZagMap;
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
		try {
			File jpegImage = new File(jpegImageFilename);
			InputStream jpegByteStream = new FileInputStream(jpegImage);

			int byteData, byteData1, byteData2;
			int markerSize;

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
						markerSize = getMarkerSize(byteData1, byteData2) - 2;
						jpegByteStream.readNBytes(markerSize);
						break;

					// DQT(Define Quantization Table)
					case 0xDB:
						// marker size
						jpegByteStream.read();
						jpegByteStream.read();

						byteData = jpegByteStream.read();
						int is16Bit = (byteData & 0xF0) >> 4;
						int tableID = byteData & 0x0F;
						QuantizationTable quantizationTable = new QuantizationTable(tableID);

						if (is16Bit == 0x1) {
							for (int i = 0; i < 64; i++) {
								byteData1 = jpegByteStream.read();
								byteData2 = jpegByteStream.read();
								quantizationTable.getData()[jpegHeader.getIndex2ZigZagMap()
										.get(i)] = (byteData1 << 8) | byteData2;
							}
						} else { // 8 bit
							for (int i = 0; i < 64; i++) {
								byteData = jpegByteStream.read();
								quantizationTable.getData()[jpegHeader.getIndex2ZigZagMap()
										.get(i)] = byteData;
							}
						}
						jpegHeader.getQuantizationTable().add(quantizationTable);
						break;

					// DHT(Define Huffman Table)
					case 0xC4:
						// marker size
						jpegByteStream.read();
						jpegByteStream.read();

						int tableInfo = jpegByteStream.read();
						int isAC = tableInfo & 0x10;
						int ID = tableInfo & 0x01;
						HuffmanTable huffmanTable = new HuffmanTable(ID);
						HashMap<Integer, List<Integer>> bitSymbolTable = huffmanTable.getBitSymbolTable();
						byte[] bytes = jpegByteStream.readNBytes(16);

						for (int i = 0; i < 16; i++) {
							int bitLength = (int) bytes[i];
							if (bitLength == 0)
								continue;

							for (int j = 0; j < bitLength; j++) {
								byteData = jpegByteStream.read();
								bitSymbolTable.get(i + 1).add(byteData); // i + 1 simply starts from index 1
							}
						}
						// TODO: can be build when building the bitSymbolTable
						huffmanTable.buildTree();
						// int[] huffmanTree = huffmanTable.getTree();
						// for (int i = 0; i < huffmanTable.getTree().length; i++) {
						// if(huffmanTree[i] != Integer.MIN_VALUE){
						// System.out.println("index: " + i + ", value: " + String.format("0x%X",
						// huffmanTree[i]));
						// }
						// }
						// System.exit(1);

						// 0b11110000
						// 0b10000000
						// int bitStream = 0b10100000;
						// int index = 0;
						// int mask = 0b10000000;
						// int bit;
						// int remainingBits = 8;
						// int symbol = huffmanTable.getSymbol(index);
						// while(symbol == Integer.MIN_VALUE) {
						// bit = bitStream & mask;
						// if(bit == 0){
						// index = (index << 1) + 1;
						// } else {
						// index = (index << 1) + 2;
						// }
						// symbol = huffmanTable.getSymbol(index);
						// mask >>= 1;
						// remainingBits--;
						// }
						// System.out.println("symbol: " + symbol + ", remainingBits: " +
						// remainingBits);
						// System.exit(1);

						// 0x16 = 00010110
						// (1, x), x is integer(positive or negative)
						// 0110 is 6 so x is 6 bits long, x

						if (isAC == 0x10) {
							jpegHeader.getACHuffmanTable().add(huffmanTable);
						} else {
							jpegHeader.getDCHuffmanTable().add(huffmanTable);
						}

						break;

					// SOF(Start of Frame) baseline
					case 0xC0:
						jpegByteStream.read();
						jpegByteStream.read();
						jpegByteStream.read();
						byteData1 = jpegByteStream.read();
						byteData2 = jpegByteStream.read();
						jpegHeader.setHeight(getHeight(byteData1, byteData2));
						byteData1 = jpegByteStream.read();
						byteData2 = jpegByteStream.read();
						jpegHeader.setWidth(getWidth(byteData1, byteData2));

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

							System.out.println("horizontal sampling factor: " + horizontalSamplingFactor
									+ ", vertical sampling factor: " + verticalSamplingFactor);

							jpegHeader.getComponents().add(component);
						}
						// System.exit(1);
						break;
					// SOS(Start of Scan)
					case 0xDA:
						byteData1 = jpegByteStream.read();
						byteData2 = jpegByteStream.read();
						markerSize = getMarkerSize(byteData1, byteData2) - 2;

						componentCount = jpegByteStream.read();
						for (int i = 0; i < componentCount; i++) {
							int componentID = jpegByteStream.read();
							byteData = jpegByteStream.read();
							int DCHuffmanTableID = (byteData & 0xF0) >> 4;
							int ACHuffmanTableID = byteData & 0x0F;

							jpegHeader.getComponents().get(componentID).setACHuffmanTableID(ACHuffmanTableID);
							jpegHeader.getComponents().get(componentID).setDCHuffmanTableID(DCHuffmanTableID);
						}
						markerSize -= (1 + componentCount * 2);
						jpegByteStream.readNBytes(jpegHeader.sos, 0, markerSize);

						byteData1 = jpegByteStream.read();
						while (jpegByteStream.available() > 0) {
							byteData2 = byteData1;
							byteData1 = jpegByteStream.read();
							if (reachMarker(byteData2)) {
								// EOI(End of Image)
								if (byteData1 == 0xD9) {
									System.out.println("End of image and size: " + jpegHeader.getData().size());
									System.out.println(
											"width:  " + jpegHeader.getWidth() + ", height: " + jpegHeader.getHeight());
									System.out.println("sos size: " + jpegHeader.sos.length);
									break;
								} else if (byteData1 == 0xFF) { // skip multiple consecutive 0xFF
									continue;
								} else if (byteData1 == 0x00) { // ignore zero after 0xFF
									jpegHeader.pushData(byteData2);
									byteData1 = jpegByteStream.read();
								} else {
									System.out.println("Error: invalid marker");
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
			jpegByteStream.close();

			// int[][] chrominance = jpegHeader.getChrominanceQuantizedTable();
			// int[][] luminance = jpegHeader.getLuminanceQuantizedTable();
			// // loop through luminance table
			// for (int i = 0; i < 8; i++) {
			// for (int j = 0; j < 8; j++) {
			// System.out.print(String.format("0x%X", luminance[i][j]) + " ");
			// }
			// System.out.println();
			// }
			// System.out.println("-----------------");
			// // loop through chrominance table
			// for (int i = 0; i < 8; i++) {
			// for (int j = 0; j < 8; j++) {
			// System.out.print(String.format("0x%X", chrominance[i][j]) + " ");
			// }
			// System.out.println();
			// }

			// loop through ACHuffmanTable
			List<HuffmanTable> ACHuffmanTable = jpegHeader.getACHuffmanTable();
			List<HuffmanTable> DCHuffmanTable = jpegHeader.getDCHuffmanTable();
			HashMap<Integer, List<Integer>> bitSymbolTable;

			System.out.println(" AC --------------------------------");
			for (int i = 0; i < ACHuffmanTable.size(); i++) {
				bitSymbolTable = ACHuffmanTable.get(i).getBitSymbolTable();
				for (int j = 0; j < 16; j++) {
					System.out.print(j + 1 + ": ");
					for (int k = 0; k < bitSymbolTable.get(j + 1).size(); k++) {
						System.out.print(String.format("0x%X", bitSymbolTable.get(j + 1).get(k)) + " ");
					}
					System.out.println();
				}
			}
			System.out.println(" DC --------------------------------");
			for (int i = 0; i < DCHuffmanTable.size(); i++) {
				bitSymbolTable = DCHuffmanTable.get(i).getBitSymbolTable();
				for (int j = 0; j < 16; j++) {
					System.out.print(j + 1 + ": ");
					for (int k = 0; k < bitSymbolTable.get(j + 1).size(); k++) {
						System.out.print(String.format("0x%X", bitSymbolTable.get(j + 1).get(k)) + " ");
					}
					System.out.println();
				}
			}

			// loop through components
			// List<Component> components = jpegHeader.getComponents();
			// for(int i = 1; i < components.size(); i++) {
			// Component component = components.get(i);
			// System.out.println("componentID: " + component.getID());
			// System.out.println("Huffman AC ID: " + component.getACHuffmanTableID());
			// System.out.println("Huffman DC ID : " + component.getDCHuffmanTableID());
			// System.out.println("hori sampling: " +
			// component.getHorizontalSamplingFactor());
			// System.out.println("ver sampling: " + component.getVerticalSamplingFactor());
			// System.out.println("quantized ID: " + component.getQuantizedTableID());
			// System.out.println("------------------------");
			// }
		} catch (Exception e) {
			System.out.println("Cannot read jpeg image");
			e.printStackTrace();
		}

		JPEGDecoder jpegDecoder = new JPEGDecoder();
		jpegDecoder.decode(jpegHeader);
		jpegDecoder.dequantize(jpegHeader);
		jpegDecoder.IDCT(jpegHeader);
		jpegDecoder.YCbCr2RGB();
		saveBMP(jpegImageFilename, jpegHeader.getWidth(), jpegHeader.getHeight(), jpegDecoder.getBlocks());

		System.out.println("block count: " + jpegDecoder.getBlocks().size());

		// List<QuantizationTable> quantizationTableList =
		// jpegHeader.getQuantizationTable();
		// for(int i = 0; i < quantizationTableList.size(); i++) {
		// QuantizationTable quantizationTable = quantizationTableList.get(i);
		// for(int j = 0; j < 64; j++) {
		// if(j % 8 == 0){
		// System.out.println();
		// }

		// System.out.print(quantizationTable.getData()[j] + " ");
		// }
		// }
	}

	public static boolean reachMarker(int byteData) {
		return byteData == 0xFF;
	}

	public static int getMarkerSize(int byteData1, int byteData2) {
		return (byteData1 << 8) | byteData2;
	}

	public static int getWidth(int byteData1, int byteData2) {
		return (byteData1 << 8) | byteData2;
	}

	public static int getHeight(int byteData1, int byteData2) {
		return (byteData1 << 8) | byteData2;
	}

	public static void usage() {
		System.out.println("Usage: java Main jpeg_filename");
	}

	public static void saveBMP(String filename, int width, int height, List<Block> blocks) {
		// File file;
		// BufferedImage image = null;
		// try {
		// file = new File(filename);
		// image = ImageIO.read(file);
		// }catch(Exception e) {
		// e.printStackTrace();
		// }

		// // Get image dimensions
		// int w= image.getWidth();
		// int h= image.getHeight();
		// System.out.println("image width: " + w + ", image height: " + h);

		// // Create a 3D array to store RGB data
		// int[][][] rgbArray = new int[h][w][3];

		// // Process the image data
		// for (int y = 0; y < h; y++) {
		// for (int x = 0; x < w; x++) {
		// int rgb = image.getRGB(x, y);

		// // Extract RGB components
		// int red = (rgb >> 16) & 0xFF;
		// int green = (rgb >> 8) & 0xFF;
		// int blue = rgb & 0xFF;

		// // Store RGB components in the array
		// rgbArray[y][x][0] = red; // Red
		// rgbArray[y][x][1] = green; // Green
		// rgbArray[y][x][2] = blue; // Blue
		// }
		// }

		// BufferedImage bufferedImage = new BufferedImage(width, height,
		// BufferedImage.TYPE_3BYTE_BGR);
		// byte[] bufferedImageBytes = ((DataBufferByte)
		// bufferedImage.getRaster().getDataBuffer()).getData();

		// int pixelIndex = 0;
		// for (int y = 0; y < h; y++) {
		// for (int x = 0; x < w; x++) {
		// for(int i = 2; i >= 0; i--) {
		// bufferedImageBytes[pixelIndex++] = (byte) rgbArray[y][x][i];
		// }
		// }
		// }

		// {
		// try {
		// int dotIndex = filename.lastIndexOf('.');
		// String basename = (dotIndex == -1) ? filename : filename.substring(0,
		// dotIndex);
		// File outputBMP = new File(basename + ".bmp");
		// ImageIO.write(bufferedImage, "bmp", outputBMP);
		// } catch (Exception e) {
		// e.printStackTrace();
		// }
		// }
		// System.exit(0);

		BufferedImage bufferedImage = new BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR);
		byte[] bufferedImageBytes = ((DataBufferByte) bufferedImage.getRaster().getDataBuffer()).getData();

		// // FIXME
		int maxBlocksCol = width / 8;
		int maxBlocksRow = height / 8;
		int prevBlockindex = 0;
		int blockIndex = 0;

		List<Byte> rgbData = new ArrayList<>();
		int idx = 0;

		for (int i = 0; i < maxBlocksCol; i++) {
			// eight line of rgb
			for (int o = 0; o < 8; o++) {
				// line of rgb of maxBlocksRow 
				for (int j = 0; j < maxBlocksRow; j++) {
					Block block = blocks.get(blockIndex);
					int r[] = block.getComponentDataByID(4);
					int g[] = block.getComponentDataByID(5);
					int b[] = block.getComponentDataByID(6);

					// 0 - 7 , 8 - 15, ..., 56 - 63
					for (int k = 0; k < 8; k++) {
						int pixelIndex = (o * 8) + k;
						rgbData.add((byte) r[pixelIndex]);
						rgbData.add((byte) g[pixelIndex]);
						rgbData.add((byte) b[pixelIndex]);
						bufferedImageBytes[idx++] = (byte) b[pixelIndex]; // blue
						bufferedImageBytes[idx++] = (byte) g[pixelIndex]; // green
						bufferedImageBytes[idx++] = (byte) r[pixelIndex]; // red
						// System.out.println("b: " + b[pixelIndex] + ", g: " + g[pixelIndex] + ", r: " + r[pixelIndex]);
					}

					if((blockIndex + 3) < blocks.size())
						blockIndex += 3;
				}

				if ((o + 1) != 8) {
					blockIndex = prevBlockindex;
				} else {
					prevBlockindex = blockIndex;
				}
			}
		}
		System.out.println("size: " + rgbData.size());
		// System.exit(0);

		try {
			int dotIndex = filename.lastIndexOf('.');
			String basename = (dotIndex == -1) ? filename : filename.substring(0, dotIndex);
			File outputBMP = new File(basename + ".bmp");
			ImageIO.write(bufferedImage, "bmp", outputBMP);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
