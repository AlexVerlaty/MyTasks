// The window. Four screens, the same four the Android app has, in the same order:
// intro → Google's sign-in → the key → (or) what went wrong.
//
// The one thing that must stay true of this file: the person's password is typed on
// Google's own page inside the WebView, and this program never sees it, never intercepts a
// keystroke and never submits a form on their behalf. All it does afterwards is read its
// own cookie jar — which is the one thing a web page cannot do, and the entire reason this
// program exists instead of a link.

using Microsoft.Web.WebView2.Core;
using Microsoft.Web.WebView2.WinForms;

namespace MyTasks.KeepLink;

internal sealed class MainForm : Form
{
    private static readonly Color Bg = Color.FromArgb(0x0D, 0x0D, 0x0D);
    private static readonly Color Surface = Color.FromArgb(0x1A, 0x1A, 0x1A);
    private static readonly Color Fg = Color.FromArgb(0xF5, 0xF5, 0xF5);
    private static readonly Color Muted = Color.FromArgb(0x9A, 0x9A, 0x9A);
    private static readonly Color Accent = Color.FromArgb(0x24, 0x62, 0xEA);

    private readonly Panel _intro = NewPanel();
    private readonly Panel _signIn = NewPanel();
    private readonly Panel _result = NewPanel();
    private readonly Panel _failure = NewPanel();

    private WebView2 _view;
    private TextBox _keyBox;
    private Label _accountLabel;
    private Label _failureLabel;
    private Label _signInHint;

    public MainForm()
    {
        Text = "MyTasks · вход в Keep";
        BackColor = Bg;
        ForeColor = Fg;
        Width = 980;
        Height = 760;
        StartPosition = FormStartPosition.CenterScreen;
        Font = new Font("Segoe UI", 10F);
        try
        {
            using var stream = Embedded("app.ico");
            if (stream is not null)
            {
                Icon = new Icon(stream);
            }
        }
        catch
        {
            // A missing icon is not a reason to refuse to start.
        }

        BuildIntro();
        BuildSignIn();
        BuildResult();
        BuildFailure();

        Controls.AddRange(new Control[] { _intro, _signIn, _result, _failure });
        Show(_intro);
    }

    /// <summary>A file baked into the exe, or null. See the csproj for why they are baked.</summary>
    private static Stream Embedded(string name) =>
        typeof(MainForm).Assembly.GetManifestResourceStream(name);

    private static Panel NewPanel() => new()
    {
        Dock = DockStyle.Fill,
        BackColor = Bg,
        Visible = false,
    };

    private void Show(Panel which)
    {
        foreach (var panel in new[] { _intro, _signIn, _result, _failure })
        {
            panel.Visible = ReferenceEquals(panel, which);
        }

        which.BringToFront();
    }

    // ── screen 1: what this is, before anything happens ──────────────────

    private void BuildIntro()
    {
        var stack = NewStack();

        var logo = new PictureBox
        {
            SizeMode = PictureBoxSizeMode.Zoom,
            Width = 96,
            Height = 96,
            BackColor = Bg,
            Margin = new Padding(0, 0, 0, 18),
        };
        try
        {
            using var stream = Embedded("logo.png");
            logo.Image = stream is null ? null : Image.FromStream(stream);
            logo.Visible = logo.Image is not null;
        }
        catch
        {
            logo.Visible = false;
        }

        stack.Controls.Add(logo);
        stack.Controls.Add(Heading("MyTasks — вход в Keep"));
        stack.Controls.Add(Body(
            "Сейчас откроется обычная страница входа Google. Пароль ты вводишь только на " +
            "ней — я его не вижу, и он мне не нужен.\n\n" +
            "После входа программа заберёт ключ и покажет его на экране. Ты скопируешь его " +
            "и отправишь боту MyTasks. Ничего на компьютере не сохраняется."));

        var start = Button("Войти в Google", Accent, Color.White);
        start.Click += async (_, _) => await StartSignInAsync();
        stack.Controls.Add(start);

        _intro.Controls.Add(stack);
    }

    // ── screen 2: Google's own page ──────────────────────────────────────

    private void BuildSignIn()
    {
        _signInHint = new Label
        {
            Dock = DockStyle.Top,
            Height = 46,
            BackColor = Surface,
            ForeColor = Fg,
            Padding = new Padding(16, 0, 16, 0),
            TextAlign = ContentAlignment.MiddleLeft,
            Text = "Войди в свой аккаунт Google ниже. Пароль вводится только на странице Google.",
        };

        _view = new WebView2 { Dock = DockStyle.Fill };
        _signIn.Controls.Add(_view);
        _signIn.Controls.Add(_signInHint);
    }

    // ── screen 3: the key ────────────────────────────────────────────────

    private void BuildResult()
    {
        var stack = NewStack();

        _accountLabel = Body("");
        stack.Controls.Add(Heading("Готово — вот ключ"));
        stack.Controls.Add(_accountLabel);

        _keyBox = new TextBox
        {
            ReadOnly = true,
            Multiline = true,
            ScrollBars = ScrollBars.Vertical,
            BackColor = Surface,
            ForeColor = Fg,
            BorderStyle = BorderStyle.FixedSingle,
            Font = new Font("Consolas", 9.5F),
            Width = 640,
            Height = 96,
            Margin = new Padding(0, 6, 0, 14),
        };
        stack.Controls.Add(_keyBox);

        var copy = Button("Скопировать", Accent, Color.White);
        copy.Click += (_, _) =>
        {
            if (_keyBox.TextLength > 0)
            {
                Clipboard.SetText(_keyBox.Text);
                copy.Text = "Скопировано ✓";
            }
        };
        stack.Controls.Add(copy);

        stack.Controls.Add(Body(
            "Отправь эту строчку боту MyTasks в личный чат — он подключит Keep и сразу " +
            "сотрёт сообщение.\n\n" +
            "Больше её никому не показывай: это постоянный ключ, он открывает весь " +
            "Google-аккаунт. Отобрать доступ можно командой /unlinkkeep в боте или на " +
            "странице myaccount.google.com/permissions.\n\n" +
            "Программа больше не нужна — её можно удалить."));

        _result.Controls.Add(stack);
    }

    // ── screen 4: it did not work ────────────────────────────────────────

    private void BuildFailure()
    {
        var stack = NewStack();
        _failureLabel = Body("");
        stack.Controls.Add(Heading("Не получилось"));
        stack.Controls.Add(_failureLabel);

        var again = Button("Ещё раз", Surface, Fg);
        again.Click += async (_, _) => await StartSignInAsync();
        stack.Controls.Add(again);

        _failure.Controls.Add(stack);
    }

    // ── the flow ─────────────────────────────────────────────────────────

    private async Task StartSignInAsync()
    {
        Show(_signIn);
        try
        {
            // A fresh profile every attempt. WebView2 persists cookies across runs, so a
            // stale oauth_token from an earlier try would otherwise be picked up instantly —
            // before the person has even finished typing — and exchanged into a failure.
            var profile = Path.Combine(
                Path.GetTempPath(), "MyTasksKeepLink", Guid.NewGuid().ToString("N"));
            var env = await CoreWebView2Environment.CreateAsync(null, profile);
            await _view.EnsureCoreWebView2Async(env);
            _view.CoreWebView2.Navigate(Protocol.EmbeddedSetupUrl);

            var found = await PollForCredentialsAsync();
            if (found is null)
            {
                Fail("Вход не завершился вовремя. Попробуй ещё раз.");
                return;
            }

            _signInHint.Text = "Меняю строчку на ключ…";
            var token = await Protocol.FetchMasterTokenAsync(found.Value.Email, found.Value.Token);
            Succeed(found.Value.Email, token);
        }
        catch (Exception ex)
        {
            Fail("Не удалось получить ключ: " + ex.Message);
        }
    }

    /// <summary>
    /// Watches the WebView's own cookie jar until the address and the cookie both appear
    /// AND stay unchanged across two consecutive looks.
    ///
    /// The second look is not paranoia: Google can drop an interim <c>oauth_token</c> right
    /// after the email step, before the password and 2FA are done, so a single sighting is
    /// not yet the final one. The Android app learned this the same way.
    /// </summary>
    private async Task<(string Email, string Token)?> PollForCredentialsAsync()
    {
        var deadline = DateTime.UtcNow + Protocol.PollTimeout;
        (string Email, string Token)? lastSeen = null;

        while (DateTime.UtcNow < deadline)
        {
            (string Email, string Token)? current = null;
            try
            {
                var cookies = await _view.CoreWebView2.CookieManager.GetCookiesAsync(
                    Protocol.EmbeddedSetupUrl);
                var cookie = cookies.FirstOrDefault(
                    c => c.Name == Protocol.CookieName &&
                         c.Value.StartsWith(Protocol.CookiePrefix, StringComparison.Ordinal));

                if (cookie is not null)
                {
                    var email = await ReadEmailAsync();
                    if (!string.IsNullOrEmpty(email))
                    {
                        current = (email, cookie.Value);
                    }
                }
            }
            catch
            {
                // Navigating between Google's own steps can tear the page down mid-read.
                // That is not a failure of the attempt; look again next tick.
            }

            if (current is not null && lastSeen is not null &&
                current.Value.Email == lastSeen.Value.Email &&
                current.Value.Token == lastSeen.Value.Token)
            {
                return current;
            }

            lastSeen = current;
            await Task.Delay(Protocol.PollInterval);
        }

        return null;
    }

    private async Task<string> ReadEmailAsync()
    {
        var raw = await _view.CoreWebView2.ExecuteScriptAsync(Protocol.EmailJs);
        if (string.IsNullOrEmpty(raw) || raw == "null")
        {
            return "";
        }

        var value = raw.Trim('"').Replace("\\u0040", "@");
        return value.Contains('@') ? value : "";
    }

    private void Succeed(string email, string token)
    {
        _accountLabel.Text = "Вход выполнен: " + email;
        _keyBox.Text = token;
        Show(_result);
    }

    private void Fail(string message)
    {
        _failureLabel.Text = message;
        Show(_failure);
    }

    // ── small widgets ────────────────────────────────────────────────────

    private static FlowLayoutPanel NewStack() => new()
    {
        Dock = DockStyle.Fill,
        FlowDirection = FlowDirection.TopDown,
        WrapContents = false,
        AutoScroll = true,
        Padding = new Padding(48, 44, 48, 44),
        BackColor = Bg,
    };

    private static Label Heading(string text) => new()
    {
        Text = text,
        ForeColor = Fg,
        Font = new Font("Segoe UI", 17F, FontStyle.Bold),
        AutoSize = true,
        MaximumSize = new Size(760, 0),
        Margin = new Padding(0, 0, 0, 12),
    };

    private static Label Body(string text) => new()
    {
        Text = text,
        ForeColor = Muted,
        AutoSize = true,
        MaximumSize = new Size(760, 0),
        Margin = new Padding(0, 0, 0, 20),
    };

    private static Button Button(string text, Color back, Color fore)
    {
        var button = new Button
        {
            Text = text,
            BackColor = back,
            ForeColor = fore,
            FlatStyle = FlatStyle.Flat,
            Height = 44,
            Width = 240,
            Font = new Font("Segoe UI", 10.5F, FontStyle.Bold),
            Margin = new Padding(0, 0, 0, 22),
            Cursor = Cursors.Hand,
        };
        button.FlatAppearance.BorderSize = 0;
        return button;
    }
}
