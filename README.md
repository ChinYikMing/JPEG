# Environment
- Tested on: macOS Sonoma 14.0
- Ubuntu 22.04

# Java SDK version
- Openjdk 21.0.1(macOS Sonoma 14.0)
- Openjdk 11.0.20.1(Ubuntu 22.04)

# Decoding time
### macOS Sonoma 14.0
- teatime.jpg: 0.32s
- monalisa.jpg: 0.20s
- gig-sn01.jpg: 0.41s
- gig-sn08.jpg: 0.52s
### Ubuntu 22.04 
- teatime.jpg: 0.25s
- monalisa.jpg: 0.14s
- gig-sn01.jpg: 0.31s
- gig-sn08.jpg: 0.35s
Note: the decoding time is averaged over 10 runs respectively

# Some improvement
- IDCT can be speed up in certain ways because I precalculate the coefficients that are used (see lines 238–252).

# Execution Guidelines
- To build executable and decode four images
```bash
$ make
```
- To build and decode four images and show all decoded images(prerequisite: `open` CLI program is installed or configured)
```bash
$ make open
```
- To build executable
```bash
$ make build
```
- To build executable and decode teatime.jpg
```bash
$ make tea
```
- To build executable and decode monalisa.jpg
```bash
$ make mona
```
- To build executable and decode gig-sn01.jpg
```bash
$ make gig01
```
- To build executable and decode gig-sn08.jpg
```bash
$ make gig08
```
- To clear all artifacts
```bash
$ make clean
```
- To run executable without using Makefile (prerequisite: at least `make build` is run before)
```bash
$ java Main image_filename  # e.g., java Main teatime.jpg
```
