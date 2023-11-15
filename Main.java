import java.io.*;

class Main {
public static void main(String[] args){
	if(args.length != 1){
		usage();
		System.exit(1);
	}
	String jpegImageFilename = args[0];
	try {
            File jpegImage = new File(jpegImageFilename);
	    int width;
	    int height;
	    int byteData, byteData1, byteData2;

	    InputStream jpegStream = new FileInputStream(jpegImage);
	    while(jpegStream.available() > 0){
		   byteData = jpegStream.read();
		   if(!reachMarker(byteData))
			   continue;

		byteData = jpegStream.read();

		switch(byteData){
			// reach a marker that contains the height and width information
			case 0xC0:
				jpegStream.read();
				jpegStream.read();
				jpegStream.read();
				byteData1 = jpegStream.read();
				byteData2 = jpegStream.read();
				width = getWidth(byteData1, byteData2);
				byteData1 = jpegStream.read();
				byteData2 = jpegStream.read();
				height = getHeight(byteData1, byteData2);
				System.out.println("width: " + width + ", height: " + height);
				break;
			default:
				break;
		}
	    }

         } catch (Exception e) {
	    System.out.println("Cannot read jpeg image");
       	    e.printStackTrace();
        }
}

public static boolean reachMarker(int byteData){
	return byteData == 0xFF;
}

public static int getWidth(int byteData1, int byteData2){
	return (byteData1 << 8) | byteData2;
}

public static int getHeight(int byteData1, int byteData2){
	return (byteData1 << 8) | byteData2;
}

public static void usage(){
	System.out.println("Usage: java Main jpeg_filename");
}
}
