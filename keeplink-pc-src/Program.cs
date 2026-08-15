// MyTasks · вход в Keep — the Windows twin of the Android app we already ship.
//
// Why a program at all, when everything else the bot connects is a link and a consent
// screen: Google Keep has no public API, so the only credential is the sign-in session's
// own `oauth_token` cookie, and that cookie is `HttpOnly` — invisible to page JavaScript
// by design (measured, `.planning/phases/29-keep-alternatives/RESEARCH.md §0.0`). A native
// host can read its own cookie jar; a web page never can. That is the whole difference,
// and it is why the alternative was making people open DevTools by hand.

namespace MyTasks.KeepLink;

internal static class Program
{
    [STAThread]
    private static void Main()
    {
        ApplicationConfiguration.Initialize();

        if (!WebViewRuntime.IsPresent())
        {
            // Naming the missing piece and where to get it, rather than dying with a
            // COM error the person cannot act on. Evergreen ships with Windows 11 and
            // with Edge, so this is rare — but «программа не открывается» with no
            // explanation is exactly the dead end this whole flow exists to remove.
            MessageBox.Show(
                "Не хватает компонента Windows — WebView2.\n\n" +
                "Он есть почти на всех Windows 10 и 11, но на этом компьютере его нет.\n" +
                "Скачать: https://go.microsoft.com/fwlink/p/?LinkId=2124703\n\n" +
                "Поставь его и запусти программу заново.",
                "MyTasks · вход в Keep",
                MessageBoxButtons.OK,
                MessageBoxIcon.Warning);
            return;
        }

        Application.Run(new MainForm());
    }
}

internal static class WebViewRuntime
{
    public static bool IsPresent()
    {
        try
        {
            return !string.IsNullOrEmpty(
                Microsoft.Web.WebView2.Core.CoreWebView2Environment
                    .GetAvailableBrowserVersionString(null));
        }
        catch
        {
            return false;
        }
    }
}
