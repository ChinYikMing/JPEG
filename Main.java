import java.io.*;
import java.util.*;

// 8x8 basic block
class Block {
	int[] y;
	int[] cb;
	int[] cr;
	int[] r;
	int[] g;
	int[] b;
	int index;

	Block() {
		this.y = new int[64];
		this.cb = new int[64];
		this.cr = new int[64];
		this.r = new int[64];
		this.g = new int[64];
		this.b = new int[64];
		this.index = 0;
	}

	public int[] getY() {
		return y;
	}

	public int[] getCb() {
		return cb;
	}

	public int[] getCr() {
		return cr;
	}

	public int[] getR() {
		return r;
	}

	public int[] getG() {
		return g;
	}

	public int[] getB() {
		return b;
	}

	public int getIndex() {
		return index;
	}

	public void setIndex(int index) {
		this.index = index;
	}
}

class JPEGDecoder {
	List<Block> blocks;

	JPEGDecoder() {
		this.blocks = new ArrayList<Block>();
	}

	public List<Block> decode(JPEGHeader header) {
		BitInputStream bitInputStream = new BitInputStream(header.data);

		// first block
		Block block = new Block();

		for (int i = 1; i < header.getComponents().size(); i++) {
			Component component = header.getComponents().get(i);
			// decode DC
			// (DC length, DC coefficient)
			System.out.println("data first byte: " + String.format("0x%X", header.data.get(0)));
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
			block.getY()[0] = dcCoeff;
			block.setIndex(block.getIndex() + 1);
			System.out.println("dclength: " + dcLength + ", dcCoeff: " + dcCoeff);
			// decode AC
			// while(block.getIndex() < 64) {
			// int symbol = huffmanDecode(header.getACHuffmanTable().get(0),
			// bitInputStream);
			// if(symbol == -1){
			// System.out.println("Error: invalid AC coefficent");
			// System.exit(1);
			// }

			// // (Most significant four bit, Least significant four bit)
			// // (preceding zero count, AC coefficient)

			// if(symbol == 0x00) { // 63 AC coefficient will be zero
			// // fill 63 zero in zig-zag order
			// break;
			// }

			// int acCoef = symbol & 0x0F;
			// int precedingZeroCount = symbol >> 4;
			// if(precedingZeroCount == 0xF0) { // it should be 16 zero even 0xF0 is 15
			// precedingZeroCount = 16;
			// }

			// if(block.getIndex() + precedingZeroCount >= 64){
			// System.out.println("Error: preceding zero count exceeding 8x8 block");
			// System.exit(1);
			// }

			// for(int i = 0; i < precedingZeroCount; i++) {

			// }
			// }
		}

		blocks.add(block);
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

	private void dequantize(int[][] quantizedTable) {

	}

	private void IDCT() {

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

class JPEGHeader {
	int width;
	int height;
	byte[] sos;
	List<Component> components;
	int[][] luminanceQuantizedTable;
	int[][] chrominanceQuantizedTable;
	List<HuffmanTable> DCHuffmanTable;
	List<HuffmanTable> ACHuffmanTable;
	List<Integer> data;

	JPEGHeader() {
		this.sos = new byte[10];
		this.luminanceQuantizedTable = new int[8][8];
		this.chrominanceQuantizedTable = new int[8][8];
		this.DCHuffmanTable = new ArrayList<HuffmanTable>();
		this.ACHuffmanTable = new ArrayList<HuffmanTable>();
		this.data = new ArrayList<Integer>();
		this.components = new ArrayList<Component>();
		this.components.add(null); // since the component ID starts from 1 so padding index 0 with null
	}

	public void pushData(int value) {
		data.add(value);
	}

	public List<Component> getComponents() {
		return components;
	}

	public int[][] getLuminanceQuantizedTable() {
		return luminanceQuantizedTable;
	}

	public int[][] getChrominanceQuantizedTable() {
		return chrominanceQuantizedTable;
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
			InputStream jpegStream = new FileInputStream(jpegImage);

			int byteData, byteData1, byteData2;
			int markerSize;

			while (jpegStream.available() > 0) {
				byteData = jpegStream.read();

				if (!reachMarker(byteData))
					continue;

				byteData = jpegStream.read();
				switch (byteData) {
					// SOI(Start of Image)
					case 0xD8:
						jpegStream.read();
						break;

					// APP0
					case 0xE0:
						byteData1 = jpegStream.read();
						byteData2 = jpegStream.read();
						markerSize = getMarkerSize(byteData1, byteData2) - 2;
						jpegStream.readNBytes(markerSize);
						break;

					// DQT(Define Quantization Table)
					case 0xDB:
						// marker size
						jpegStream.read();
						jpegStream.read();

						byteData = jpegStream.read();
						// luminance
						if (byteData == 0x00) {
							for (int i = 0; i < 8; i++) {
								for (int j = 0; j < 8; j++) {
									byteData = jpegStream.read();
									jpegHeader.luminanceQuantizedTable[i][j] = byteData;
								}
							}
						} else { // chrominance
							for (int i = 0; i < 8; i++) {
								for (int j = 0; j < 8; j++) {
									byteData = jpegStream.read();
									jpegHeader.chrominanceQuantizedTable[i][j] = byteData;
								}
							}
						}
						break;

					// DHT(Define Huffman Table)
					case 0xC4:
						// marker size
						jpegStream.read();
						jpegStream.read();

						int tableInfo = jpegStream.read();
						int isAC = tableInfo & 0x10;
						int ID = tableInfo & 0x01;
						HuffmanTable huffmanTable = new HuffmanTable(ID);
						HashMap<Integer, List<Integer>> bitSymbolTable = huffmanTable.getBitSymbolTable();
						byte[] bytes = jpegStream.readNBytes(16);

						for (int i = 0; i < 16; i++) {
							int bitLength = (int) bytes[i];
							if (bitLength == 0)
								continue;

							for (int j = 0; j < bitLength; j++) {
								byteData = jpegStream.read();
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

					// SOF(Start of Frame)
					case 0xC0:
						jpegStream.read();
						jpegStream.read();
						jpegStream.read();
						byteData1 = jpegStream.read();
						byteData2 = jpegStream.read();
						jpegHeader.setHeight(getHeight(byteData1, byteData2));
						byteData1 = jpegStream.read();
						byteData2 = jpegStream.read();
						jpegHeader.setWidth(getWidth(byteData1, byteData2));

						int componentCount = jpegStream.read();
						// jpegHeader.setComponentCount(componentCount);
						for (int i = 0; i < componentCount; i++) {
							Component component = new Component();
							int componentID = jpegStream.read();
							byteData = jpegStream.read();
							int horizontalSamplingFactor = (byteData & 0xF0) >> 4;
							int verticalSamplingFactor = byteData & 0x0F;
							int quantizeID = jpegStream.read();

							component.setID(componentID);
							component.setHorizontalSamplingFactor(horizontalSamplingFactor);
							component.setVerticalSamplingFactor(verticalSamplingFactor);
							;
							component.setQuantizedTableID(quantizeID);

							jpegHeader.getComponents().add(component);
						}

						// HashMap<Integer, Integer> map =
						// jpegHeader.getComponentHorizontalSamplingFactorMap();
						// HashMap<Integer, Integer> map1 =
						// jpegHeader.getComponentVerticalSamplingFactorMap();
						// HashMap<Integer, Integer> map2 = jpegHeader.getComponentQuantizedMap();
						// System.out.println("horizontal sampling factor");
						// for(int i = 1; i <= map.size(); i++){
						// System.out.println("componentID: " + i);
						// System.out.println("vertical sampling factor: " + map1.get(i));
						// System.out.println("horizontal sampling factor: " + map.get(i));
						// System.out.println("quantize table ID: " + map2.get(i));
						// System.out.println("--------------------------------");
						// }
						// System.exit(0);
						break;
					// SOS(Start of Scan)
					case 0xDA:
						byteData1 = jpegStream.read();
						byteData2 = jpegStream.read();
						markerSize = getMarkerSize(byteData1, byteData2) - 2;

						componentCount = jpegStream.read();

						for (int i = 0; i < componentCount; i++) {
							int componentID = jpegStream.read();
							byteData = jpegStream.read();
							int DCHuffmanTableID = (byteData & 0xF0) >> 4;
							int ACHuffmanTableID = byteData & 0x0F;
							// System.out.println("ID:" + componentID + ", AC: " + ACHuffmanTableID + ", DC:
							// " + DCHuffmanTableID);

							jpegHeader.getComponents().get(componentID).setACHuffmanTableID(ACHuffmanTableID);
							;
							jpegHeader.getComponents().get(componentID).setDCHuffmanTableID(DCHuffmanTableID);
							;
						}
						markerSize -= (1 + componentCount * 2);
						jpegStream.readNBytes(jpegHeader.sos, 0, markerSize);

						// print jpeg header componentDCHuffmanMap
						// System.out.println("component count: " + jpegHeader.getComponentCount());
						// System.out.println("AC component map");
						// HashMap<Integer, Integer> map= jpegHeader.getComponentACHuffmanMap();
						// for(int i = 1; i <= map.size(); i++) {
						// System.out.println("ComponentID: " + i + ", ACHuffmanTableID: " +
						// map.get(i));
						// }
						// System.out.println("DC component map");
						// map= jpegHeader.getComponentDCHuffmanMap();
						// for(int i = 1; i <= map.size(); i++) {
						// System.out.println("ComponentID: " + i + ", DCHuffmanTableID: " +
						// map.get(i));
						// }
						// System.exit(0);

						// TODO: read once with calculating the exact byte size
						while (jpegStream.available() > 0) {
							byteData = jpegStream.read();
							if (reachMarker(byteData)) {
								byteData1 = jpegStream.read();
								// EOI(End of Image)
								if (byteData1 == 0xD9) {
									System.out.println("End of image and size: " + jpegHeader.getData().size());
									System.out.println(
											"width:  " + jpegHeader.getWidth() + ", height: " + jpegHeader.getHeight());
									System.out.println("sos size: " + jpegHeader.sos.length);
									break;
								} else {
									jpegHeader.pushData(byteData1);
								}
							}

							jpegHeader.pushData(byteData);
						}
						break;

					default:
						break;
				}
			}
			jpegStream.close();

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
}
