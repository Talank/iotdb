/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.iotdb.os.fileSystem;

import org.apache.iotdb.os.conf.ObjectStorageConfig;
import org.apache.iotdb.os.conf.ObjectStorageDescriptor;
import org.apache.iotdb.os.exception.ObjectStorageException;
import org.apache.iotdb.os.io.ObjectStorageConnector;
import org.apache.iotdb.os.io.aws.S3ObjectStorageConnector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileFilter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import java.util.Arrays;

import static org.apache.iotdb.os.utils.ObjectStorageConstant.FILE_SEPARATOR;

public class OSFile extends File {
  private static final Logger logger = LoggerFactory.getLogger(OSFile.class);
  private static final String UNSUPPORT_OPERATION =
      "Current object storage file doesn't support this operation.";
  private static final ObjectStorageConfig config =
      ObjectStorageDescriptor.getInstance().getConfig();
  private static final ObjectStorageConnector connector;

  static {
    switch (config.getOsType()) {
      case AWS_S3:
        connector = new S3ObjectStorageConnector();
        break;
      default:
        connector = null;
    }
  }

  private final OSURI osUri;

  private long length = 0L;

  public OSFile(String pathname) {
    super(pathname);
    this.osUri = new OSURI(pathname);
  }

  public OSFile(String parent, String child) {
    super(parent, child);
    this.osUri = new OSURI(concatPath(parent, child));
  }

  public OSFile(File parent, String child) {
    super(parent, child);
    this.osUri = new OSURI(concatPath(parent.toString(), child));
  }

  public OSFile(URI uri) {
    super(uri);
    this.osUri = new OSURI(uri);
  }

  private String concatPath(String parent, String child) {
    if (parent.endsWith(FILE_SEPARATOR)) {
      return parent + child;
    } else {
      return parent + FILE_SEPARATOR + child;
    }
  }

  public OSFile(OSURI osUri) {
    super(osUri.getURI());
    this.osUri = osUri;
  }

  @Override
  public String getName() {
    return osUri.getKey();
  }

  @Override
  public String getParent() {
    File parent = getParentFile();
    return parent == null ? null : parent.toString();
  }

  @Override
  public File getParentFile() {
    int lastSeparatorIdx = osUri.getKey().lastIndexOf(FILE_SEPARATOR);
    if (lastSeparatorIdx <= 0) {
      return null;
    }
    return new OSFile(new OSURI(osUri.getBucket(), osUri.getKey().substring(0, lastSeparatorIdx)));
  }

  @Override
  public String getPath() {
    return osUri.toString();
  }

  @Override
  public boolean isAbsolute() {
    return true;
  }

  @Override
  public String getAbsolutePath() {
    return osUri.toString();
  }

  @Override
  public File getAbsoluteFile() {
    return this;
  }

  @Override
  public String getCanonicalPath() throws IOException {
    return osUri.toString();
  }

  @Override
  public File getCanonicalFile() throws IOException {
    return this;
  }

  @Override
  public URL toURL() throws MalformedURLException {
    throw new UnsupportedOperationException(UNSUPPORT_OPERATION);
  }

  @Override
  public URI toURI() {
    return osUri.getURI();
  }

  @Override
  public boolean canRead() {
    return this.exists();
  }

  @Override
  public boolean canWrite() {
    return this.exists();
  }

  @Override
  public boolean exists() {
    try {
      return connector.doesObjectExist(osUri);
    } catch (ObjectStorageException e) {
      logger.error("Fail to get object {}.", osUri, e);
      return false;
    }
  }

  @Override
  public boolean isDirectory() {
    return false;
  }

  @Override
  public boolean isFile() {
    return true;
  }

  @Override
  public boolean isHidden() {
    throw new UnsupportedOperationException(UNSUPPORT_OPERATION);
  }

  @Override
  public long lastModified() {
    try {
      return connector.getMetaData(osUri).lastModified();
    } catch (ObjectStorageException e) {
      logger.error("Fail to get lastModified of the object {}.", osUri, e);
      return 0;
    }
  }

  @Override
  public long length() {
    if (length == 0) {
      try {
        length = connector.getMetaData(osUri).length();
      } catch (ObjectStorageException e) {
        logger.error("Fail to get length of the object {}.", osUri, e);
        return 0;
      }
    }
    return length;
  }

  @Override
  public boolean createNewFile() throws IOException {
    try {
      return connector.createNewEmptyObject(osUri);
    } catch (ObjectStorageException e) {
      logger.error("Fail to create new object {}.", osUri, e);
      return false;
    }
  }

  @Override
  public boolean delete() {
    try {
      return connector.delete(osUri);
    } catch (ObjectStorageException e) {
      logger.error("Fail to delete object {}.", osUri, e);
      return false;
    }
  }

  @Override
  public void deleteOnExit() {
    throw new UnsupportedOperationException(UNSUPPORT_OPERATION);
  }

  @Override
  public String[] list() {
    throw new UnsupportedOperationException(UNSUPPORT_OPERATION);
  }

  @Override
  public String[] list(FilenameFilter filter) {
    throw new UnsupportedOperationException(UNSUPPORT_OPERATION);
  }

  @Override
  public File[] listFiles() {
    return super.listFiles();
  }

  @Override
  public File[] listFiles(FilenameFilter filter) {
    throw new UnsupportedOperationException(UNSUPPORT_OPERATION);
  }

  @Override
  public File[] listFiles(FileFilter filter) {
    throw new UnsupportedOperationException(UNSUPPORT_OPERATION);
  }

  @Override
  public boolean mkdir() {
    throw new UnsupportedOperationException(UNSUPPORT_OPERATION);
  }

  @Override
  public boolean mkdirs() {
    throw new UnsupportedOperationException(UNSUPPORT_OPERATION);
  }

  @Override
  public boolean renameTo(File dest) {
    OSURI targetOSUri = ((OSFile) dest).osUri;
    try {
      return connector.renameTo(osUri, targetOSUri);
    } catch (ObjectStorageException e) {
      logger.error("Fail to rename object from {} to {}.", osUri, targetOSUri, e);
      return false;
    }
  }

  @Override
  public boolean setLastModified(long time) {
    throw new UnsupportedOperationException(UNSUPPORT_OPERATION);
  }

  @Override
  public boolean setReadOnly() {
    throw new UnsupportedOperationException(UNSUPPORT_OPERATION);
  }

  @Override
  public boolean setWritable(boolean writable, boolean ownerOnly) {
    throw new UnsupportedOperationException(UNSUPPORT_OPERATION);
  }

  @Override
  public boolean setWritable(boolean writable) {
    throw new UnsupportedOperationException(UNSUPPORT_OPERATION);
  }

  @Override
  public boolean setReadable(boolean readable, boolean ownerOnly) {
    throw new UnsupportedOperationException(UNSUPPORT_OPERATION);
  }

  @Override
  public boolean setReadable(boolean readable) {
    throw new UnsupportedOperationException(UNSUPPORT_OPERATION);
  }

  @Override
  public boolean setExecutable(boolean executable, boolean ownerOnly) {
    throw new UnsupportedOperationException(UNSUPPORT_OPERATION);
  }

  @Override
  public boolean setExecutable(boolean executable) {
    throw new UnsupportedOperationException(UNSUPPORT_OPERATION);
  }

  @Override
  public boolean canExecute() {
    throw new UnsupportedOperationException(UNSUPPORT_OPERATION);
  }

  @Override
  public long getTotalSpace() {
    throw new UnsupportedOperationException(UNSUPPORT_OPERATION);
  }

  @Override
  public long getFreeSpace() {
    throw new UnsupportedOperationException(UNSUPPORT_OPERATION);
  }

  @Override
  public long getUsableSpace() {
    throw new UnsupportedOperationException(UNSUPPORT_OPERATION);
  }

  @Override
  public int compareTo(File pathname) {
    return this.toString().compareTo(pathname.toString());
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    OSFile other = (OSFile) obj;
    return osUri.equals(other.osUri);
  }

  @Override
  public int hashCode() {
    return osUri.hashCode();
  }

  @Override
  public String toString() {
    return osUri.toString();
  }

  @Override
  public Path toPath() {
    throw new UnsupportedOperationException(UNSUPPORT_OPERATION);
  }

  public OSURI toOSURI() {
    return osUri;
  }

  public BufferedReader getBufferedReader() {
    try {
      return new BufferedReader(new InputStreamReader(connector.getInputStream(osUri)));
    } catch (ObjectStorageException e) {
      logger.error("Fail to open input stream for object {}.", osUri, e);
      return null;
    }
  }

  public BufferedWriter getBufferedWriter(boolean append) {
    throw new UnsupportedOperationException(UNSUPPORT_OPERATION);
  }

  public BufferedInputStream getBufferedInputStream() {
    try {
      return new BufferedInputStream(connector.getInputStream(osUri));
    } catch (ObjectStorageException e) {
      logger.error("Fail to open input stream for object {}.", osUri, e);
      return null;
    }
  }

  public BufferedOutputStream getBufferedOutputStream() {
    throw new UnsupportedOperationException(UNSUPPORT_OPERATION);
  }

  public File[] listFilesBySuffix(String fileFolder, String suffix) {
    try {
      OSURI[] osUris = connector.list(new OSURI(fileFolder));
      return Arrays.stream(osUris)
          .filter(uri -> uri.toString().endsWith(suffix))
          .map(OSFile::new)
          .toArray(OSFile[]::new);
    } catch (ObjectStorageException e) {
      logger.error("Fail to list objects under the object {} by suffix {}.", osUri, suffix, e);
      return null;
    }
  }

  public File[] listFilesByPrefix(String fileFolder, String prefix) {
    try {
      OSURI[] osUris = connector.list(new OSURI(fileFolder));
      return Arrays.stream(osUris)
          .filter(uri -> uri.toString().startsWith(concatPath(fileFolder, prefix)))
          .map(OSFile::new)
          .toArray(OSFile[]::new);
    } catch (ObjectStorageException e) {
      logger.error("Fail to list objects under the object {} by prefix {}.", osUri, prefix, e);
      return null;
    }
  }
}
