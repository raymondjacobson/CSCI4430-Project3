#!/usr/bin/python
import os, re, sys
from lib import traverse

# Ensure script is invoked correctly
if len(sys.argv) != 2:
  print "Error. Invoke: python getStats.py directory"
  exit(1);

# Get base directory from sys argument
base_directory = sys.argv[1]

# Build list of subdirectory data & print
sub_directories = traverse(base_directory)
for directory in sub_directories:
  print directory
  print str(sub_directories[directory]['size']) + ' bytes'
  print str(sub_directories[directory]['public']) + ' public'
  print str(sub_directories[directory]['private']) + ' private'
  print str(sub_directories[directory]['try']) + ' try'
  print str(sub_directories[directory]['catch']) + ' catch'
  print ''
