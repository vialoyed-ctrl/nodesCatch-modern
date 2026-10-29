using System;
using System.Collections.Generic;
using System.Text.RegularExpressions;
using Newtonsoft.Json;
using nodesCatchNext.Mode;

namespace nodesCatchNext.Handler;

public static class ExportNaming
{
    private static readonly Regex Speed = new Regex(@"^\s*(\d+(?:[.,]\d+)?)\s*(GB/s|MB/s|KB/s|B/s)\s*$", RegexOptions.IgnoreCase);
    private static readonly Regex Prefix = new Regex(@"^\d+(?:[.,]\d+)?\s*(?:GB/s|MB/s|KB/s|B/s)\s+", RegexOptions.IgnoreCase);

    public static string Name(VmessItem node)
    {
        string original = node.remarks ?? "";
        Match match = Speed.Match(node.testResult ?? "");
        if (!match.Success) return original;
        string speed = match.Groups[1].Value.Replace(',', '.') + match.Groups[2].Value.ToUpperInvariant().Replace("/S", "/s");
        return speed + " " + Prefix.Replace(original, "", 1);
    }

    public static string ShareUrl(Config config, int index)
    {
        // Export a private copy so copying links cannot rename the live list.
        var node = JsonConvert.DeserializeObject<VmessItem>(JsonConvert.SerializeObject(config.vmess[index]));
        node.remarks = Name(node);
        return ShareHandler.GetShareUrl(new Config { localPort = config.localPort, vmess = new List<VmessItem> { node } }, 0);
    }
}
