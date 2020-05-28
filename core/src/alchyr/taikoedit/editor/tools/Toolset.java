package alchyr.taikoedit.editor.tools;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Toolset {
    private final List<EditorTool> tools;

    public Toolset(EditorTool... tools)
    {
        this.tools = new ArrayList<>();
        this.tools.addAll(Arrays.asList(tools));
    }

    public EditorTool getDefaultTool()
    {
        if (tools.isEmpty())
            return null;
        return tools.get(0);
    }

    public EditorTool getTool(int index)
    {
        return tools.get(index);
    }

    public int size()
    {
        return tools.size();
    }

    public boolean containsTool(EditorTool tool)
    {
        return tools.contains(tool);
    }
}
