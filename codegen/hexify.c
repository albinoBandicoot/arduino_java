#include <stdio.h>
#include <stdlib.h>
#include <stdint.h>

#define BASE_ADDR 0x000

int filelen (FILE *f) {
	fseek (f, 0, SEEK_END);
	int res = ftell (f);
	rewind (f);
	return res;
}

uint8_t val (char x) {
	if (x >= '0' && x <= '9') return x - '0';
	if (x >= 'a' && x <= 'f') return x - 'a' + 10;
	if (x >= 'A' && x <= 'F') return x - 'A' + 10;
	printf ("Invalid character: %c\n", x);
	return 0;
}

void write_page (FILE *out, uint8_t *buf, int page, int dlen) {
	int plen = 32;
	uint8_t checksum = 0;
	uint16_t addr = BASE_ADDR + page*32;
	if (dlen - page*32 < 32) plen = dlen - page*32;
	fprintf (out, ":%02hhX%04hX00", (uint8_t) plen, addr);
	checksum += plen;
	checksum += addr & 0xff;
	checksum += addr >> 8;
	for (int i=0; i < plen; i++) {
		uint8_t v = buf[page*32 + i];
		fprintf (out, "%02hhX", v);
		checksum += v;
	}
	fprintf (out, "%02hhX\n", -checksum);
}

int main (int argc, const char *argv[]) {
	FILE *in; 
	FILE *out;
	if (argc == 3) {
		in  = fopen (argv[1], "r");
		out = fopen (argv[2], "w");
	} else {
		in  = stdin;
		out = fopen (argv[1], "w");
	}
	int len = filelen(in);
	char *buf = malloc (len);
	fread (buf, 1, len, in);
	printf ("Read %d bytes\n", len);
	int dlen = 0;
	uint8_t *data = calloc(1, len/2 + 1);
	int hi = 1;
	for (int i=0; i < len; i++) {
		if (buf[i] == ' ' || buf[i] == '\t' || buf[i] == '\n') continue;
		if (hi) {
			data[dlen] = val(buf[i]) << 4;
		} else {
			data[dlen] |= val(buf[i]);
			printf ("Got byte %d = %02hhX\n", dlen, data[dlen]);
			dlen++;
		}
		hi = 1-hi;
	}
	printf ("Dlen = %d\n", dlen);
	int rem = dlen;
	int page = 0;
	while (rem > 0) {
		write_page (out, data, page, dlen);
		rem -= 32;
		page++;
	}
	fprintf (out, ":00000001FF");
	fclose (in);
	fclose (out);
}
