// The credential half, kept apart from the window.
//
// This is a straight port of the Android app we already ship
// (`site/keeplink-src/.../MainActivity.kt`, itself a fork of Villoh/goopdl-auth, MIT).
// Same two Google endpoints, same form fields, same single-use cookie exchanged for the
// same `aas_et/` master token. Nothing new is invented here: the Android side has been
// proven end to end, and a desktop path that spoke a slightly different protocol would be
// a second thing to debug for no gain.
//
// Two network destinations exist in this program and both are Google's:
//   accounts.google.com/EmbeddedSetup   — the sign-in page, shown to the person
//   android.clients.google.com/auth     — the token exchange
// There is no third. The key is never written to disk, never logged, never sent anywhere.

using System.Net;
using System.Text;

namespace MyTasks.KeepLink;

internal static class Protocol
{
    public const string EmbeddedSetupUrl = "https://accounts.google.com/EmbeddedSetup";
    public const string AuthUrl = "https://android.clients.google.com/auth";

    /// <summary>The signed-in address, read off the page's own profile marker.</summary>
    public const string EmailJs =
        "document.querySelector('[data-profile-identifier][data-email]')" +
        "?.getAttribute('data-email') || ''";

    /// <summary>How long to keep watching for the cookie before giving up.</summary>
    public static readonly TimeSpan PollTimeout = TimeSpan.FromMinutes(5);

    public static readonly TimeSpan PollInterval = TimeSpan.FromMilliseconds(700);

    public const string CookieName = "oauth_token";
    public const string CookiePrefix = "oauth2_4/";
    public const string MasterPrefix = "aas_et/";

    /// <summary>
    /// The exchange: a single-use <c>oauth2_4/</c> cookie for a permanent <c>aas_et/</c>
    /// master token. Google's device-auth endpoint, the same call the Android app makes.
    /// </summary>
    public static async Task<string> FetchMasterTokenAsync(string email, string oauthToken)
    {
        var fields = new Dictionary<string, string>
        {
            ["Email"] = email,
            ["Token"] = oauthToken,
            ["ACCESS_TOKEN"] = "1",
            ["add_account"] = "1",
            ["callerPkg"] = "com.google.android.gms",
            ["callerSig"] = "38918a453d07199354f8b19af05ec6562ced5788",
            ["device_country"] = "us",
            ["droidguard_results"] = "null",
            ["get_accountid"] = "1",
            ["google_play_services_version"] = "240913000",
            ["lang"] = "en",
            ["sdk_version"] = "28",
            ["service"] = "ac2dm",
        };

        var body = string.Join(
            "&",
            fields.Select(kv => $"{WebUtility.UrlEncode(kv.Key)}={WebUtility.UrlEncode(kv.Value)}"));

        using var http = new HttpClient { Timeout = TimeSpan.FromSeconds(30) };
        using var content = new StringContent(body, Encoding.UTF8);
        content.Headers.ContentType = new System.Net.Http.Headers.MediaTypeHeaderValue(
            "application/x-www-form-urlencoded");
        content.Headers.ContentType.CharSet = null;

        using var request = new HttpRequestMessage(HttpMethod.Post, AuthUrl) { Content = content };
        request.Headers.TryAddWithoutValidation("app", "com.google.android.gms");
        request.Headers.TryAddWithoutValidation("User-Agent", "");
        request.Headers.TryAddWithoutValidation("Accept-Encoding", "identity");

        using var response = await http.SendAsync(request);
        var text = await response.Content.ReadAsStringAsync();

        // The endpoint answers in `key=value` lines, not JSON.
        var values = new Dictionary<string, string>(StringComparer.Ordinal);
        foreach (var line in text.Split('\n'))
        {
            var idx = line.IndexOf('=');
            if (idx > 0)
            {
                values[line[..idx]] = line[(idx + 1)..].TrimEnd('\r');
            }
        }

        if (values.TryGetValue("Token", out var token) && token.Length > 0)
        {
            return token;
        }

        // Google's own words, surfaced as-is: `BadAuthentication` means the cookie was
        // already used or has aged out, and saying which is more use than «не получилось».
        // The cookie itself is never echoed — that is the rule token_exchange.py follows.
        if (values.TryGetValue("Error", out var error))
        {
            throw new InvalidOperationException(error);
        }

        throw new InvalidOperationException($"NoAASToken (HTTP {(int)response.StatusCode})");
    }

    /// <summary>The <c>oauth_token</c> value out of a raw cookie header, or null.</summary>
    public static string ExtractOauthToken(string cookieHeader)
    {
        if (string.IsNullOrEmpty(cookieHeader))
        {
            return null;
        }

        foreach (var part in cookieHeader.Split(';'))
        {
            var piece = part.Trim();
            if (piece.StartsWith($"{CookieName}={CookiePrefix}", StringComparison.Ordinal))
            {
                return piece[(piece.IndexOf('=') + 1)..];
            }
        }

        return null;
    }
}
