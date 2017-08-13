#!/bin/bash
java Assembler $1 | ./hexify out.hex
if [ $2 -eq '-u' ]; then
	avrdude -v -p atmega328p -c arduino -P /dev/cu.usbmodem1451 -b 115200 -D -u -U flash:w:out.hex:i
fi
