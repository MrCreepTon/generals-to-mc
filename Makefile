PYTHON ?= python
MC ?=
NATIVE_BIN ?=
OUTPUT ?=

.DEFAULT_GOAL := all
.NOTPARALLEL:
.PHONY: all build native release package source versions check smoke help

all build native release package source versions check smoke help:
	$(PYTHON) -X utf8 tools/build.py $@ --minecraft "$(MC)" --native-bin "$(NATIVE_BIN)" --output "$(OUTPUT)"
