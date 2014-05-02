import os, re, sys

def traverse(path):
  """recursively traverses given directory
  returns array with nested sub directories"""
  subdirs = {}
  for root, dirs, files in os.walk(path):
    subdirs[root] = getSubData(root)
  return subdirs

def getSubData(directory):
  """recursively gets all files in a directory (nested)
  and returns a list of them with data about keywords & sizes"""
  subdata = {}
  subdata['files'] = []
  subdata['size'] = subdata['public'] = subdata['private'] = subdata['try'] = subdata['catch'] = 0

  # Loop through nested directories
  for root, dirs, files in os.walk(directory):
    # Loop through files in directory
    for file in files:
      # Filter on .java extension
      if os.path.splitext(file)[1] == '.java':
        subdata['files'].append(file)
        file_name = root + '/' + file
        subdata['size'] += os.path.getsize(file_name)

        # Open current file and read contents into string
        with open(file_name, 'r') as my_file:
          file_data = my_file.read()

        # Delete all instances of comments & string literals
        file_data = re.sub('(\/\*([^*]|[\r\n]|(\*+([^*\/]|[\r\n])))*\*+\/|\/\/.*)', '', file_data)
        file_data = re.sub('".*"', '', file_data)

        # Calculate total occurences of java keywords
        subdata['public'] += len(re.findall('((^|\s|;)public\s)', file_data))
        subdata['private'] += len(re.findall('((^|\s|;)private\s)', file_data))
        subdata['try'] += len(re.findall('((^|\s|;|})try(\s*{|{))', file_data))
        subdata['catch'] += len(re.findall('(^|\s|;|})(catch\(|catch\s*\()', file_data))

  return subdata
