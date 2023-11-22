all:
	javac Main.java
	java  Main $(JPEG)

clean:
	rm Main.class
