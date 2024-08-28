package alchyr.taikoedit.management.assets;

import com.badlogic.gdx.utils.StreamUtils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class FileHelper {
    public static String concat(String... parts)
    {
        StringBuilder result = new StringBuilder();

        for (int i = 0; i < parts.length; ++i)
        {
            if (i < parts.length - 1 || !parts[i].contains(".")) //If last element has no extension, will be treated as a path
            {
                result.append(withSeparator(parts[i]));
            }
            else
            {
                result.append(parts[i]);
            }
        }
        return result.toString();
    }

    public static String removeInvalidChars(String filename) {
        return filename.replaceAll("[\\\\/:*?\"<>|]", "");
    }

    public static String getFileExtension(String filename) {
        int separatorIndex = filename.lastIndexOf('.');
        return separatorIndex == -1 ? "" : filename.substring(separatorIndex + 1);
    }

    public static String withSeparator(String path)
    {
        return path + (path.endsWith(File.separator) ? "" : File.separator);
    }

    public static String gdxSeparator(String path)
    {
        return path.replace(File.separator, "/");
    }

    public static boolean isImage(File f)
    {
        if (f.isFile())
        {
            return isImageFilename(f.getName());
        }
        return false;
    }
    public static boolean isImageFilename(String filename)
    {
        if (filename.contains("."))
        {
            switch (filename.substring(filename.lastIndexOf('.')))
            {
                case ".jpg":
                case ".jpeg":
                case ".png":
                    return true;
            }
        }
        return false;
    }

    public static List<String> readFileLines(File f)
    {
        if (f.isFile() && f.canRead())
        {
            FileInputStream in = null;
            InputStreamReader reader = null;
            try
            {
                in = new FileInputStream(f);
                reader = new InputStreamReader(in, StandardCharsets.UTF_8);

                ArrayList<String> lines = new ArrayList<>();
                StringBuilder line = new StringBuilder();

                if (reader.ready())
                {
                    int c = reader.read();
                    while (c != -1)
                    {
                        char ch = (char)c;
                        if (ch == '\n')
                        {
                            lines.add(line.toString());
                            line = new StringBuilder();
                        }
                        else if (ch != '\r') //ignore carriage return
                        {
                            line.append(ch);
                        }
                        c = reader.read();
                    }
                }

                reader.close();
                in.close();
                return lines;
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
            finally {
                StreamUtils.closeQuietly(reader);
                StreamUtils.closeQuietly(in);
            }
        }
        return null;
    }

    public static List<String> readFileLines(File f, String stopLine)
    {
        if (f.isFile() && f.canRead())
        {
            InputStream fStream = null;
            try
            {
                fStream = Files.newInputStream(f.toPath());
                BufferedReader reader = new BufferedReader(new InputStreamReader(fStream), 4096); //1/2 size of default buffer
                //smaller buffer is used as with stopLine the assumed amount to be read is smaller, especially in the single use of this method

                ArrayList<String> lines = new ArrayList<>();
                String line;
                while ((line = reader.readLine()) != null) {
                    lines.add(line);
                    if (line.equals(stopLine))
                        break;
                }

                fStream.close();

                return lines;
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
            finally {
                StreamUtils.closeQuietly(fStream);
            }
        }
        return null;
    }

    public static List<String> readFileLinesOld(File f, String stopLine)
    {
        if (f.isFile() && f.canRead())
        {
            FileReader reader = null;
            try
            {
                reader = new FileReader(f);

                ArrayList<String> lines = new ArrayList<>();
                StringBuilder line = new StringBuilder();

                if (reader.ready())
                {
                    int c = reader.read();
                    while (c != -1)
                    {
                        char ch = (char)c;
                        if (ch == '\n')
                        {
                            String lineText = line.toString();
                            lines.add(lineText);
                            line.delete(0, lineText.length());

                            if (lineText.equals(stopLine))
                                break;
                        }
                        else if (ch != '\r') //ignore carriage return
                        {
                            line.append(ch);
                        }
                        c = reader.read();
                    }
                }

                reader.close();

                return lines;
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
            finally {
                StreamUtils.closeQuietly(reader);
            }
        }
        return null;
    }
}
