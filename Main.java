import java.io.*;
import java.util.*;

class HuffmanTable {
	int ID;
	HashMap<Integer, List<Integer>> bitSymbolTable;

	HuffmanTable(int ID) {
		this.ID = ID;
		this.bitSymbolTable = new HashMap<Integer, List<Integer>>();
		for (int i = 0; i < 16; i++) {
			this.bitSymbolTable.put(i + 1, new ArrayList<Integer>()); // i + 1 simply starts from index 1
		}
	}

	public int getID() {
		return ID;
	}

	public HashMap<Integer, List<Integer>> getBitSymbolTable() {
		return bitSymbolTable;
	}
}

class JPEGHeader {
	int width;
	int height;
	byte[] sos;
	int componentCount;
	HashMap<Integer, Integer> componentQuantizedMap;
	HashMap<Integer, Integer> componentACHuffmanMap;
	HashMap<Integer, Integer> componentDCHuffmanMap;
	int[][] luminanceQuantizedTable;
	int[][] chrominanceQuantizedTable;
	List<HuffmanTable> DCHuffmanTable;
	List<HuffmanTable> ACHuffmanTable;
	List<Integer> data;

	JPEGHeader() {
		this.sos = new byte[10];
		this.componentQuantizedMap = new HashMap<Integer, Integer>();
		this.componentACHuffmanMap = new HashMap<Integer, Integer>();
		this.componentDCHuffmanMap = new HashMap<Integer, Integer>();
		this.luminanceQuantizedTable = new int[8][8];
		this.chrominanceQuantizedTable = new int[8][8];
		this.DCHuffmanTable = new ArrayList<HuffmanTable>();
		this.ACHuffmanTable = new ArrayList<HuffmanTable>();
		this.data = new ArrayList<Integer>();
	}

	public void pushData(int value) {
		data.add(value);
	}

	public HashMap<Integer, Integer> getComponentACHuffmanMap() {
		return componentACHuffmanMap;
	}

	public HashMap<Integer, Integer> getComponentDCHuffmanMap() {
		return componentDCHuffmanMap;
	}

	public HashMap<Integer, Integer> getComponentQuantizedMap() {
		return componentQuantizedMap;
	}

	public int getComponentCount() {
		return componentCount;
	}

	public void setComponentCount(int componentCount) {
		this.componentCount = componentCount;
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
		try {
			File jpegImage = new File(jpegImageFilename);
			JPEGHeader jpegHeader = new JPEGHeader();
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

						if (isAC == 0x10) {
							jpegHeader.ACHuffmanTable.add(huffmanTable);
						} else {
							jpegHeader.DCHuffmanTable.add(huffmanTable);
						}

						break;

					// SOF(Start of Frame)
					case 0xC0:
						jpegStream.read();
						jpegStream.read();
						jpegStream.read();
						byteData1 = jpegStream.read();
						byteData2 = jpegStream.read();
						jpegHeader.setWidth(getWidth(byteData1, byteData2));
						byteData1 = jpegStream.read();
						byteData2 = jpegStream.read();
						jpegHeader.setHeight(getHeight(byteData1, byteData2));
						break;
					// SOS(Start of Scan)
					case 0xDA:
						byteData1 = jpegStream.read();
						byteData2 = jpegStream.read();
						markerSize = getMarkerSize(byteData1, byteData2) - 2;

						int componentCount = jpegStream.read();
						jpegHeader.setComponentCount(componentCount);

						for (int i = 0; i < componentCount; i++) {
							int componentID = jpegStream.read();
							byteData = jpegStream.read();
							int DCHuffmanTableID = (byteData & 0xF0) >> 4;
							int ACHuffmanTableID = byteData & 0x0F;
							// System.out.println("ID:" + componentID + ", AC: " + ACHuffmanTableID + ", DC: " + DCHuffmanTableID);

							jpegHeader.getComponentDCHuffmanMap().put(componentID, DCHuffmanTableID);
							jpegHeader.getComponentACHuffmanMap().put(componentID, ACHuffmanTableID);
						}
						markerSize -= (1 + componentCount * 2);
						jpegStream.readNBytes(jpegHeader.sos, 0, markerSize);

						// print jpeg header componentDCHuffmanMap
						// System.out.println("component count: " + jpegHeader.getComponentCount());
						// System.out.println("AC component map");
						// HashMap<Integer, Integer> map= jpegHeader.getComponentACHuffmanMap();
						// for(int i = 1; i <= map.size(); i++) {
						// 	System.out.println("ComponentID: " + i + ", ACHuffmanTableID: " + map.get(i));
						// }
						// System.out.println("DC component map");
						// map= jpegHeader.getComponentDCHuffmanMap();
						// for(int i = 1; i <= map.size(); i++) {
						// 	System.out.println("ComponentID: " + i + ", DCHuffmanTableID: " + map.get(i));
						// }
						// System.exit(0);

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
		} catch (Exception e) {
			System.out.println("Cannot read jpeg image");
			e.printStackTrace();
		}
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
