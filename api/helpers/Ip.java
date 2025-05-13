package org.nms.api.helpers;

import io.vertx.core.json.JsonArray;

import java.net.*;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

public class Ip
{
    private static final int MAX_IP_COUNT = 1024;

    // Returns a JSON array of IPs based on the input IP and type
    public static JsonArray getIpListAsJsonArray(String ip, String ipType)
    {
        var jsonArray = new JsonArray();
        List<String> ipList = null;

        if (ip == null || ipType == null)
        {
            return jsonArray;
        }

        switch (ipType.toUpperCase())
        {
            case "SINGLE":
                if (isValidIp(ip))
                {
                    jsonArray.add(ip);
                }
                break;

            case "RANGE":
                if (ip.contains("-"))
                {
                    String[] range = ip.split("-");
                    if (range.length == 2)
                    {
                        String startIp = range[0].trim();
                        String endIp = range[1].trim();
                        ipList = getIpListForRange(startIp, endIp);
                    }
                }
                break;

            case "CIDR":
                if (ip.contains("/"))
                {
                    String[] CIDR = ip.split("/");

                    if (CIDR.length == 2)
                    {
                        String ipPart = CIDR[0].trim();
                        try
                        {
                            int prefixLength = Integer.parseInt(CIDR[1].trim());
                            ipList = getIpListForCIDR(ipPart, prefixLength);
                        }
                        catch (NumberFormatException e)
                        {
                            // Invalid prefix length
                        }
                    }
                }
                break;

            default:
                break;
        }

        if (ipList != null && !ipList.isEmpty())
        {
            for (String ipAddress : ipList)
            {
                jsonArray.add(ipAddress);
            }
        }

        return jsonArray;
    }

    // Check if IP address is valid
    public static boolean isValidIp(String ip)
    {
        if (ip == null || ip.isEmpty())
        {
            return false;
        }

        try
        {
            InetAddress.getByName(ip);

            return true;
        }
        catch (UnknownHostException e)
        {
            return false;
        }
    }

    // Check if the IP address and its type are valid
    public static boolean isValidIpAndType(String ip, String ipType)
    {
        if (ip == null || ipType == null)
        {
            return true;
        }

        switch (ipType)
        {
            case "SINGLE":
                return !isValidIp(ip);

            case "RANGE":
                if (!ip.contains("-"))
                {
                    return true;
                }

                var range = ip.split("-");

                if (range.length != 2)
                {
                    return true;
                }

                var startIp = range[0].trim();
                var endIp = range[1].trim();

                if (!isValidIp(startIp) || !isValidIp(endIp))
                {
                    return true;
                }

                try
                {
                    var startAddr = InetAddress.getByName(startIp);
                    var endAddr = InetAddress.getByName(endIp);

                    if (startAddr.getAddress().length != endAddr.getAddress().length)
                    {
                        return true;
                    }

                    var ipList = getIpListForRange(startIp, endIp);
                    return ipList == null || ipList.size() > MAX_IP_COUNT;
                }
                catch (UnknownHostException e)
                {
                    return true;
                }

            case "CIDR":
                if (!ip.contains("/"))
                {
                    return true;
                }

                String[] CIDR = ip.split("/");

                if (CIDR.length != 2)
                {
                    return true;
                }

                var ipPart = CIDR[0].trim();

                if (!isValidIPv4(ipPart))
                {
                    return true;
                }

                try
                {
                    var prefixLength = Integer.parseInt(CIDR[1].trim());

                    if (prefixLength < 0 || prefixLength > 32)
                    {
                        return true;
                    }

                    var ipCount = 1L << (32 - prefixLength);

                    if (ipCount > MAX_IP_COUNT)
                    {
                        return true;
                    }

                    var ipList = getIpListForCIDR(ipPart, prefixLength);
                    return ipList == null || ipList.size() > MAX_IP_COUNT;
                }
                catch (NumberFormatException e)
                {
                    return true;
                }

            default:
                return true;
        }
    }

    // Check if IPv4 address is valid
    public static boolean isValidIPv4(String ip)
    {
        if (ip == null || ip.isEmpty())
        {
            return false;
        }

        try
        {
            var inetAddress = InetAddress.getByName(ip);
            return inetAddress.getAddress().length == 4;
        }
        catch (UnknownHostException e)
        {
            return false;
        }
    }

    // Get list of IPs for a given range
    public static List<String> getIpListForRange(String startIp, String endIp)
    {
        try
        {
            var startAddr = InetAddress.getByName(startIp);
            var endAddr = InetAddress.getByName(endIp);

            var startBytes = startAddr.getAddress();
            var endBytes = endAddr.getAddress();

            var startNum = new BigInteger(1, startBytes);
            var endNum = new BigInteger(1, endBytes);

            if (startNum.compareTo(endNum) > 0)
            {
                return null;
            }

            var rangeSize = endNum.subtract(startNum).add(BigInteger.ONE);
            if (rangeSize.compareTo(BigInteger.valueOf(MAX_IP_COUNT)) > 0)
            {
                return null;
            }

            var ipList = new ArrayList<String>();

            var currentNum = startNum;
            while (currentNum.compareTo(endNum) <= 0)
            {
                var ipBytes = bigIntegerToBytes(currentNum, startBytes.length);

                ipList.add(InetAddress.getByAddress(ipBytes).getHostAddress());

                currentNum = currentNum.add(BigInteger.ONE);
            }

            return ipList;
        }
        catch (UnknownHostException e)
        {
            return null;
        }
    }

    // Get list of IPs for a given CIDR
    public static List<String> getIpListForCIDR(String ip, int prefixLength)
    {
        try
        {
            var addr = InetAddress.getByName(ip);

            var ipBytes = addr.getAddress();

            if (ipBytes.length != 4)
            {
                return null; // Only IPv4 supported
            }

            var ipCount = 1L << (32 - prefixLength);

            if (ipCount > MAX_IP_COUNT)
            {
                return null;
            }

            var ipNum = bytesToLong(ipBytes);
            var mask = (0xFFFFFFFFL << (32 - prefixLength)) & 0xFFFFFFFFL;
            var network = ipNum & mask;

            var ipList = new ArrayList<String>();

            for (var i = 0; i < ipCount; i++)
            {
                var currentIp = network + i;

                var currentBytes = longToBytes(currentIp);

                ipList.add(InetAddress.getByAddress(currentBytes).getHostAddress());
            }
            return ipList;
        }
        catch (UnknownHostException e)
        {
            return null;
        }
    }

    // Convert byte array to long (for IPv4)
    private static long bytesToLong(byte[] bytes)
    {
        var result = 0;

        for (var b : bytes)
        {
            result = (result << 8) | (b & 0xFF);
        }

        return result;
    }

    // Convert long to byte array (for IPv4)
    private static byte[] longToBytes(long value)
    {
        var bytes = new byte[4];

        for (var i = 3; i >= 0; i--)
        {
            bytes[i] = (byte) (value & 0xFF);
            value >>= 8;
        }

        return bytes;
    }

    // Convert BigInteger to byte array with specified length
    private static byte[] bigIntegerToBytes(BigInteger num, int length)
    {
        var bytes = num.toByteArray();

        var result = new byte[length];

        var srcOffset = Math.max(0, bytes.length - length);

        var destOffset = length - Math.min(bytes.length, length);

        System.arraycopy(bytes, srcOffset, result, destOffset, Math.min(bytes.length, length));

        return result;
    }
}