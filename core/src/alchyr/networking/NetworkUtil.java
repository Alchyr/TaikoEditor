package alchyr.networking;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;

public class NetworkUtil {
    private static final String[] providers = new String[] {
            "https://checkip.amazonaws.com",
            "https://ipv4.icanhazip.com/",
            "https://ipecho.net/plain"
    };
    public static String getPublicIP() throws IOException {
        Set<String> IPs = new HashSet<>();
        try {
            for (String provider : providers) {
                URL whatismyip = new URL(provider);
                try (BufferedReader in = new BufferedReader(new InputStreamReader(
                            whatismyip.openStream()))) {
                    String ip = in.readLine();
                    if (!IPs.add(ip)) { //same IP received twice, should be accurate
                        return ip;
                    }
                } catch (Exception ignored) { }
            }
        } catch (MalformedURLException e) {
            throw new RuntimeException(e); //Shouldn't happen
        }
        throw new IOException("Failed to determine public IP address.");
    }
}
