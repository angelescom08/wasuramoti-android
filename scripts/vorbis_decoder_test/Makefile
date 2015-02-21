VERSION = 1
LIBDIR = libs
ROOT = $(shell git rev-parse --show-toplevel)
FILES = stb_vorbis decode_file

$(LIBDIR)/libstbvorbis.so: $(addsuffix .o, $(addprefix $(LIBDIR)/, $(FILES)))
	gcc -shared -Wl,-soname,$(@F).$(VERSION) -o $@ $^

$(LIBDIR)/%.o: $(ROOT)/src/main/jni/%.c
	mkdir -p $(LIBDIR)
	gcc -c -fPIC $< -o $@

.PHONY: clean
clean:
	rm -rf $(LIBDIR)
