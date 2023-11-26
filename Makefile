all: build tea mona gig01 gig08

open: all
	open teatime.bmp
	open monalisa.bmp
	open gig-sn01.bmp
	open gig-sn08.bmp

build:
	javac Main.java

tea: build
	java Main teatime.jpg

mona: build
	java Main monalisa.jpg

gig01: build
	java Main gig-sn01.jpg

gig08: build
	java Main gig-sn08.jpg

clean:
	rm *.class
	rm *.bmp
