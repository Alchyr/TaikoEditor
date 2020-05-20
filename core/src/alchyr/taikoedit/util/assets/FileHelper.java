package alchyr.taikoedit.util.assets;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class FileHelper {
    public static String concat(String... parts)
    {
        StringBuilder result = new StringBuilder();

        for (int i = 0; i < parts.length; ++i)
        {
            if (i < parts.length - 1 || !parts[i].contains("."))
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
            try
            {
                FileReader reader = new FileReader(f);

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

                return lines;
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        }
        return null;
    }
}
