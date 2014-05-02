import os, re, sys

def traverse(path):
  """recursively traverses given directory
  returns array with nested sub directories"""
  subdirs = {}
  for root, dirs, files in os.walk(path):
    subdirs[root] = getSubFiles(root)
  return subdirs


def getSubFiles(directory):
  """recursively gets all files in a directory (nested)
  and returns a list of them"""
  subfiles = []
  for root, dirs, files in os.walk(directory):
    for f in files:
      if os.path.splitext(f)[1] == '.java':
        subfiles.append(f)
  return subfiles
