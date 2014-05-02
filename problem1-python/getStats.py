#!/usr/bin/python
import os, re, sys
from lib import traverse

# Ensure script is invoked correctly
if len(sys.argv)!=2:
  print "Error. Invoke: python getStats.py directory"
  exit(1);

base_directory = sys.argv[1]
sub_directories = traverse(base_directory)
for k in sub_directories:
  print k
  print sub_directories[k]
  print ""
