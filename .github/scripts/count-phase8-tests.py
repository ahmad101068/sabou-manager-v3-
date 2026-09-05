#!/usr/bin/env python3
import sys
from pathlib import Path
import xml.etree.ElementTree as ET
print(sum(int(ET.parse(p).getroot().attrib.get('tests',0)) for p in Path(sys.argv[1]).rglob('TEST-*.xml')))
