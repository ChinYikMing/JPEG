import java.io.*;
import java.util.*;

class JPEGHeader {
	int width;
	int height;
	byte[] sos;
	int[][] luminanceQuantizedTable;
	int[][] chrominanceQuantizedTable;
	List<Integer> data;

	JPEGHeader() {
		this.sos = new byte[10];
		this.luminanceQuantizedTable = new int[8][8];
		this.chrominanceQuantizedTable = new int[8][8];
		this.data = new ArrayList<Integer>();
	}

	public void pushData(int value) {
		data.add(value);
	}

	public int[][] getLuminanceQuantizedTable() {
		return luminanceQuantizedTable;
	}

	public int[][] getChrominanceQuantizedTable() {
		return chrominanceQuantizedTable;
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
						if(byteData == 0x00){
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
						jpegStream.readNBytes(jpegHeader.sos, 0, markerSize);

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
			// 	for (int j = 0; j < 8; j++) {
            //         System.out.print(String.format("0x%X", luminance[i][j]) + " ");
            //     }
            //     System.out.println();
            // }
			// System.out.println("-----------------");
			// // loop through chrominance table
			// for (int i = 0; i < 8; i++) {
			// 	for (int j = 0; j < 8; j++) {
            //         System.out.print(String.format("0x%X", chrominance[i][j]) + " ");
            //     }
            //     System.out.println();
            // }
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
