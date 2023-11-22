all:
	javac Main.java
	java  Main $(JPEG)

clean:
	rm *.class
	rm *.bmp
