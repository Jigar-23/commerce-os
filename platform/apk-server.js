const http = require('http');
const fs = require('fs');
const path = require('path');
const { exec } = require('child_process');

const PORT = 8888;
const CUSTOMER_APK = path.resolve(__dirname, '../apps/android/app/build/outputs/apk/debug/app-debug.apk');
const RIDER_APK = path.resolve(__dirname, '../apps/android/rider-app/build/outputs/apk/debug/rider-app-debug.apk');
const ADB_PATH = `${process.env.HOME}/Library/Android/sdk/platform-tools/adb`;

// Continuous background ADB auto-installer
setInterval(() => {
  exec(`${ADB_PATH} connect 192.168.1.3:5555 && ${ADB_PATH} devices`, (err, stdout) => {
    if (stdout.includes('192.168.1.3:5555\tdevice')) {
      console.log('📱 [ADB] S26 AUTHORIZED! Automatically installing CommerceOS APK...');
      exec(`${ADB_PATH} -s 192.168.1.3:5555 install -r -d -g "${CUSTOMER_APK}"`, (iErr, iOut) => {
        if (!iErr) {
          console.log('✅ [ADB] Successfully installed app on S26! Launching app...');
          exec(`${ADB_PATH} -s 192.168.1.3:5555 shell am start -n com.commerceos.android/.MainActivity`);
        }
      });
    }
  });
}, 3000);

const server = http.createServer((req, res) => {
  if (req.url === '/app.apk' || req.url === '/download') {
    if (fs.existsSync(CUSTOMER_APK)) {
      res.writeHead(200, {
        'Content-Type': 'application/vnd.android.package-archive',
        'Content-Disposition': 'attachment; filename="CommerceOS-Customer-App.apk"',
        'Content-Length': fs.statSync(CUSTOMER_APK).size
      });
      return fs.createReadStream(CUSTOMER_APK).pipe(res);
    }
  }

  if (req.url === '/rider.apk') {
    if (fs.existsSync(RIDER_APK)) {
      res.writeHead(200, {
        'Content-Type': 'application/vnd.android.package-archive',
        'Content-Disposition': 'attachment; filename="CommerceOS-Rider-App.apk"',
        'Content-Length': fs.statSync(RIDER_APK).size
      });
      return fs.createReadStream(RIDER_APK).pipe(res);
    }
  }

  res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
  res.end(`<!DOCTYPE html>
<html>
<head>
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Commerce OS — Install on Galaxy S26</title>
  <script src="https://cdn.tailwindcss.com"></script>
</head>
<body class="bg-slate-950 text-white min-h-screen flex flex-col items-center justify-center p-6 text-center">
  <div class="max-w-md w-full bg-slate-900 border border-slate-800 rounded-3xl p-8 shadow-2xl space-y-6">
    <div class="w-16 h-16 bg-emerald-500 rounded-2xl mx-auto flex items-center justify-center text-3xl font-black text-slate-950 shadow-lg shadow-emerald-500/30">
      ⚡
    </div>
    <div>
      <h1 class="text-2xl font-black tracking-tight">Commerce<span class="text-emerald-400">OS</span></h1>
      <p class="text-xs text-slate-400 mt-1">Live Cloud Connected • Samsung Galaxy S26 Ready</p>
    </div>
    
    <div class="space-y-3 pt-2">
      <a href="/app.apk" class="block w-full py-4 px-6 rounded-2xl bg-emerald-500 hover:bg-emerald-400 text-slate-950 font-black text-sm transition-transform active:scale-95 shadow-lg shadow-emerald-500/20">
        📥 1-Tap Download Customer App (.apk)
      </a>
      <a href="/rider.apk" class="block w-full py-3.5 px-6 rounded-2xl bg-slate-800 hover:bg-slate-700 text-white font-bold text-xs border border-slate-700 transition-all">
        🛵 Download Rider Partner App (.apk)
      </a>
    </div>

    <p class="text-[11px] text-slate-500">
      Auto-configured to connect to: <span class="text-emerald-400 font-mono">https://commerce-os-api.onrender.com</span>
    </p>
  </div>
</body>
</html>`);
});

server.listen(PORT, '0.0.0.0', () => {
  console.log(`📱 APK Download & Auto-Installer Server running on http://0.0.0.0:${PORT}`);
});
