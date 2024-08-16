package alchyr.networking.p2p;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URL;

public class NetworkUtil {
    private static final String[] providers = new String[] {
            "http://checkip.amazonaws.com",
            "http://myexternalip.com/raw",
            "http://www.trackip.net/ip"
    };
    public static String getPublicIP() {
        for (String provider : providers) {
            BufferedReader in = null;
            try {
                URL whatismyip = new URI(provider).toURL();
                in = new BufferedReader(new InputStreamReader(
                        whatismyip.openStream()));
                String ip = in.readLine();
                in.close();
                in = null;
                return ip;
            } catch (Exception ignored) { }
            finally
            {
                if (in != null) {
                    try {
                        in.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        return null;
    }
}
