all:
	javac -cp jai-1_1_2_01/lib/jai_codec.jar:jai-1_1_2_01/lib/jai_core.jar:jai-1_1_2_01/lib/mlibwrapper_jai.jar:. Main.java
	java -cp jai-1_1_2_01/lib/jai_codec.jar:jai-1_1_2_01/lib/jai_core.jar:jai-1_1_2_01/lib/mlibwrapper_jai.jar:. Main $(JPEG)
