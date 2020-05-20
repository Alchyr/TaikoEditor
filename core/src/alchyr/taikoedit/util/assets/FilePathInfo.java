package alchyr.taikoedit.util.assets;

import java.util.Objects;

public class FilePathInfo {
    //used for loading localization with dynamic paths based on language
    public String path;
    public String filename;

    public FilePathInfo(String path, String filename)
    {
        this.path = path;
        this.filename = filename;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FilePathInfo that = (FilePathInfo) o;
        return path.equals(that.path) &&
                filename.equals(that.filename);
    }

    @Override
    public int hashCode() {
        return Objects.hash(path, filename);
    }
}
