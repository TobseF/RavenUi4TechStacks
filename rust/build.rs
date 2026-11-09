// The raven-gui crate links libraven.a and user32/gdi32/shell32/comdlg32/ole32;
// the static library needs a few more system import libraries (UI Automation
// GUIDs, WinHTTP image loader, COM variants).
fn main() {
    for lib in ["uuid", "oleaut32", "advapi32", "winhttp"] {
        println!("cargo:rustc-link-arg=-l{}", lib);
    }
}
