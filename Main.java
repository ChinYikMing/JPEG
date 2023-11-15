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
	    System.out.println("file size: " + jpegImage.length());
         } catch (Exception e) {
	    System.out.println("Cannot read jpeg image");
       	    e.printStackTrace();
        }
}
public static void usage(){
	System.out.println("Usage: java Main jpeg_filename");
}
}
