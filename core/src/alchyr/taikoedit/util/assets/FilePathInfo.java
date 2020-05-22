package alchyr.taikoedit.util.assets;

import java.util.Objects;

public class FilePathInfo {
    //used for loading localization with dynamic paths based on language
    public String path;
    public String filename;

    private int hash = 0;

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
        if (hash == 0)
            hash = Objects.hash(path, filename);
        return hash;
    }
}
