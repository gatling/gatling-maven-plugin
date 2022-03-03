
/*
 * Copyright 2011-2022 GatlingCorp (https://gatling.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gatling.mojo.util;

import java.io.*;
import java.util.function.Predicate;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.apache.commons.io.IOUtils;

public class Zip {

  public static void unzipFile(File file, File targetDirectory, Predicate<String> exclude)
      throws IOException {
    try (ZipInputStream zis =
        new ZipInputStream(new BufferedInputStream(new FileInputStream(file)))) {
      ZipEntry zipEntry = zis.getNextEntry();
      while (zipEntry != null) {
        if (!exclude.test(zipEntry.getName())) {
          File newFile = new File(targetDirectory, zipEntry.getName());

          if (!newFile
              .getCanonicalPath()
              .startsWith(targetDirectory.getCanonicalPath() + File.separator)) {
            throw new IOException(
                "ZIP slip!!! Entry is outside of the target dir: " + zipEntry.getName());
          }

          if (zipEntry.isDirectory()) {
            if (!newFile.isDirectory() && !newFile.mkdirs()) {
              throw new IOException("Failed to create directory " + newFile);
            }
          } else {
            File parent = newFile.getParentFile();
            if (!parent.isDirectory() && !parent.mkdirs()) {
              throw new IOException("Failed to create directory " + parent);
            }

            try (OutputStream fos = new BufferedOutputStream(new FileOutputStream(newFile))) {
              IOUtils.copy(zis, fos);
            }
          }
        }
        zipEntry = zis.getNextEntry();
      }
      zis.closeEntry();
    }
  }

  public static void zipDirectory(File directory, File targetFile) throws IOException {
    try (ZipOutputStream zipOut =
        new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(targetFile)))) {
      // we don't directly zip the directory itself because we don't want it as a root entry
      for (File childFile : directory.listFiles()) {
        zipFile(childFile, childFile.getName(), zipOut);
      }
    }
  }

  private static void zipFile(File fileToZip, String fileName, ZipOutputStream zipOut)
      throws IOException {
    if (fileToZip.isHidden()) {
      return;
    }
    if (fileToZip.isDirectory()) {
      String normalizedFileName = fileName.endsWith("/") ? fileName : fileName + "/";
      zipOut.putNextEntry(new ZipEntry(normalizedFileName));
      zipOut.closeEntry();
      for (File childFile : fileToZip.listFiles()) {
        zipFile(childFile, fileName + "/" + childFile.getName(), zipOut);
      }
    } else {
      ZipEntry zipEntry = new ZipEntry(fileName);
      zipOut.putNextEntry(zipEntry);

      try (InputStream is = new BufferedInputStream(new FileInputStream(fileToZip))) {
        IOUtils.copy(is, zipOut);
      }
    }
  }
}
