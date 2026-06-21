// WebViewHelper.cs
// Windows native helper for OpenAudioMC headless browser integration.
//
// Creates a hidden Form with WebView2 control, reads JSON commands from stdin,
// executes them, and writes JSON responses to stdout. Audio plays through the
// system mixer via WebView2's Chromium audio engine.
//
// Prerequisites:
//   - .NET 6+ SDK
//   - WebView2 Runtime (ships with Windows 10/11, or install from Microsoft)
//
// Build:
//   dotnet new console -n WebViewHelper
//   cd WebViewHelper
//   dotnet add package Microsoft.Web.WebView2
//   (replace Program.cs with this file)
//   dotnet publish -c Release -r win-x64 --self-contained -p:PublishSingleFile=true
//
// Protocol: Same as macOS helper (see WebViewHelper.swift)

using System;
using System.Collections.Generic;
using System.IO;
using System.Text;
using System.Text.Json;
using System.Threading;
using System.Threading.Tasks;
using System.Windows.Forms;
using Microsoft.Web.WebView2.WinForms;
using Microsoft.Web.WebView2.Core;

class WebViewHelper : Form
{
    private WebView2 webView;
    private bool isReady = false;

    public WebViewHelper()
    {
        // Hidden form (1x1 pixel, offscreen)
        this.Text = "WebViewHelper";
        this.Width = 1;
        this.Height = 1;
        this.ShowInTaskbar = false;
        this.WindowState = FormWindowState.Minimized;
        this.Opacity = 0;

        webView = new WebView2();
        webView.Dock = DockStyle.Fill;
        this.Controls.Add(webView);
    }

    protected override async void OnLoad(EventArgs e)
    {
        base.OnLoad(e);

        try
        {
            // Initialize WebView2 with autoplay enabled
            var env = await CoreWebView2Environment.CreateAsync(null, null,
                new CoreWebView2EnvironmentOptions("--autoplay-policy=no-user-gesture-required"));
            await webView.EnsureCoreWebView2Async(env);

            // Allow mixed content
            webView.CoreWebView2.Settings.AreBrowserAcceleratorKeysEnabled = false;
            webView.CoreWebView2.Settings.AreDefaultContextMenusEnabled = false;
            webView.CoreWebView2.Settings.UserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) NotRidingAlert/1.0 WebView2";

            webView.CoreWebView2.NavigationCompleted += OnNavigationCompleted;

            isReady = true;
            WriteLine(JsonLine("ready", null));

            // Start reading stdin on a background thread
            _ = Task.Run(() => ReadLoop());
        }
        catch (Exception ex)
        {
            WriteLine(JsonLine("error", new { message = ex.Message }));
            Application.Exit();
        }
    }

    private void OnNavigationCompleted(object sender, CoreWebView2NavigationCompletedEventArgs e)
    {
        var url = webView.CoreWebView2.Source;
        WriteLine(JsonLine("loaded", new { url = url, success = e.IsSuccess }));
    }

    private void ReadLoop()
    {
        string line;
        while ((line = Console.ReadLine()) != null)
        {
            if (string.IsNullOrWhiteSpace(line)) continue;

            try
            {
                using var doc = JsonDocument.Parse(line);
                var root = doc.RootElement;
                var cmd = root.GetProperty("cmd").GetString();

                switch (cmd)
                {
                    case "load":
                        var url = root.GetProperty("url").GetString();
                        this.Invoke(() => LoadUrl(url));
                        break;

                    case "eval":
                        var js = root.GetProperty("js").GetString();
                        var id = root.GetProperty("id").GetString();
                        this.Invoke(() => EvalJs(js, id));
                        break;

                    case "quit":
                        this.Invoke(() => Application.Exit());
                        return;

                    default:
                        break;
                }
            }
            catch (Exception)
            {
            }
        }

        this.Invoke(() => Application.Exit());
    }

    private void LoadUrl(string url)
    {
        if (!isReady) return;
        webView.CoreWebView2.Navigate(url);
    }

    private async void EvalJs(string js, string id)
    {
        if (!isReady)
        {
            WriteLine(JsonLine("eval_result", new { id = id, result = new { error = "not ready" } }));
            return;
        }

        try
        {
            var resultJson = await webView.CoreWebView2.ExecuteScriptAsync(js);
            // ExecuteScriptAsync returns a JSON string. Parse and re-emit.
            using var resultDoc = JsonDocument.Parse(resultJson);
            var resultObj = resultDoc.RootElement;

            // Build response
            var response = new Dictionary<string, object>
            {
                ["type"] = "eval_result",
                ["id"] = id
            };

            // If the result is an object, embed it directly; otherwise wrap in {value: ...}
            if (resultObj.ValueKind == JsonValueKind.Object)
            {
                response["result"] = JsonSerializer.Deserialize<Dictionary<string, object>>(resultJson);
            }
            else
            {
                response["result"] = new Dictionary<string, object> { ["value"] = resultJson };
            }

            var json = JsonSerializer.Serialize(response);
            Console.Out.WriteLine(json);
            Console.Out.Flush();
        }
        catch (Exception ex)
        {
            var errorResult = new { type = "eval_result", id = id, result = new { error = ex.Message } };
            Console.Out.WriteLine(JsonSerializer.Serialize(errorResult));
            Console.Out.Flush();
        }
    }

    private static string JsonLine(string type, object data)
    {
        var dict = new Dictionary<string, object> { ["type"] = type };
        if (data != null)
        {
            foreach (var prop in data.GetType().GetProperties())
            {
                dict[prop.Name] = prop.GetValue(data);
            }
        }
        var json = JsonSerializer.Serialize(dict);
        return json;
    }

    private static void WriteLine(string line)
    {
        Console.Out.WriteLine(line);
        Console.Out.Flush();
    }

    [STAThread]
    static void Main()
    {
        Application.EnableVisualStyles();
        Application.SetCompatibleTextRenderingDefault(false);
        Application.Run(new WebViewHelper());
    }
}
