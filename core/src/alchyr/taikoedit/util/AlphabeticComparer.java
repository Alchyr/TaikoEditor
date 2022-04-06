package alchyr.taikoedit.util;

import java.util.Comparator;

//Sorter for strings prioritizing alphabet position over capitalization.
//Specifically, this comparator adjusts the order of the set of characters between 'A' and 'z', which includes some symbols.
//Symbols are considered to come first, followed by letters.
//If strings are identical ignoring capitalization, then capitalization is applied.
public class AlphabeticComparer implements Comparator<String> {
    private static final int symbolSection = 'Z' + 1;
    private static final int symbolSectionSize = 'a' - symbolSection;
    private static char upp(int offset) {
        return (char) (symbolSectionSize + 'A' + offset * 2);
    }
    private static char lwr(int offset) {
        return (char) (symbolSectionSize + 'B' + offset * 2);
    }
    private static char rough(char base) {
        if (base >= 'A' && base <= 'z') {
            if (base <= 'Z')
                return upp(base - 'A');
            else if (base >= 'a')
                return upp(base - 'a');
            else //middle symbol section
                return (char) (base - symbolSection);
        }
        else {
            return base;
        }
    }
    private static char fine(char base) {
        if (base >= 'A' && base <= 'z') {
            if (base <= 'Z')
                return upp(base - 'A');
            else if (base >= 'a')
                return lwr(base - 'a');
            else //middle symbol section
                return (char) (base - symbolSection);
        }
        else {
            return base;
        }
    }

    @Override
    public int compare(String a, String b) {
        int len1 = a.length();
        int len2 = b.length();
        int lim = Math.min(len1, len2);
        int fineResult = 0;

        for (int i = 0; i < lim; ++i) {
            char c1 = rough(a.charAt(i));
            char c2 = rough(b.charAt(i));
            if (c1 != c2) {
                return c1 - c2;
            }
            else if (fineResult == 0) {
                c1 = fine(a.charAt(i));
                c2 = fine(b.charAt(i));
                fineResult = c1 - c2;
            }
        }

        return len1 == len2 ? fineResult : len1 - len2;
    }
}
