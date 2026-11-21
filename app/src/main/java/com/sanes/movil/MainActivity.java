package com.sanes.movil;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Insets;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.webkit.WebViewAssetLoader;

/**
 * Pantalla única de la app: muestra la interfaz web guardada en la carpeta
 * assets dentro de un WebView a pantalla completa.
 */
public class MainActivity extends Activity {

    /** Dirección interna con la que el WebView sirve los archivos de assets. */
    private static final String ORIGEN = "appassets.androidplatform.net";
    private static final String INICIO = "https://" + ORIGEN + "/assets/index.html";

    private static final int ELEGIR_ARCHIVO = 7;

    private WebView web;
    private FrameLayout raiz;
    private ValueCallback<Uri[]> archivoPendiente;

    @SuppressLint("SetJavaScriptEnabled")
    @SuppressWarnings("deprecation")
    @Override
    protected void onCreate(Bundle estado) {
        super.onCreate(estado);

        // Dibujar detrás de las barras del sistema en todas las versiones
        // (Android 15 lo hace siempre) y dejar el hueco con márgenes.
        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }

        raiz = new FrameLayout(this);
        raiz.setBackgroundColor(getResources().getColor(R.color.barra));
        web = new WebView(this);
        raiz.addView(web, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(raiz);

        // Márgenes para la barra de estado, la de navegación y el teclado.
        raiz.setOnApplyWindowInsetsListener((v, insets) -> {
            int izq, arr, der, abj;
            if (Build.VERSION.SDK_INT >= 30) {
                Insets i = insets.getInsets(WindowInsets.Type.systemBars()
                        | WindowInsets.Type.displayCutout()
                        | WindowInsets.Type.ime());
                izq = i.left; arr = i.top; der = i.right; abj = i.bottom;
            } else {
                izq = insets.getSystemWindowInsetLeft();
                arr = insets.getSystemWindowInsetTop();
                der = insets.getSystemWindowInsetRight();
                abj = insets.getSystemWindowInsetBottom();
            }
            v.setPadding(izq, arr, der, abj);
            // Ya están aplicados como márgenes: el WebView no debe sumarlos otra vez.
            return Build.VERSION.SDK_INT >= 30 ? WindowInsets.CONSUMED : insets.consumeSystemWindowInsets();
        });

        WebSettings ajustes = web.getSettings();
        ajustes.setJavaScriptEnabled(true);
        ajustes.setDomStorageEnabled(true);      // los datos de prueba se guardan aquí
        ajustes.setAllowFileAccess(false);
        ajustes.setAllowContentAccess(false);

        final WebViewAssetLoader cargador = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView vista, WebResourceRequest peticion) {
                return cargador.shouldInterceptRequest(peticion.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView vista, WebResourceRequest peticion) {
                Uri destino = peticion.getUrl();
                if (ORIGEN.equals(destino.getHost())) {
                    return false;                // páginas de la propia app
                }
                // Enlaces externos (Google, correo...) se abren en su aplicación.
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, destino));
                } catch (ActivityNotFoundException e) {
                    Toast.makeText(MainActivity.this, R.string.sin_app, Toast.LENGTH_SHORT).show();
                }
                return true;
            }
        });

        // Elegir una foto de la galería para el perfil (<input type="file">).
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView vista, ValueCallback<Uri[]> respuesta,
                                             FileChooserParams parametros) {
                if (archivoPendiente != null) {
                    archivoPendiente.onReceiveValue(null);
                }
                archivoPendiente = respuesta;
                try {
                    startActivityForResult(parametros.createIntent(), ELEGIR_ARCHIVO);
                } catch (ActivityNotFoundException e) {
                    archivoPendiente = null;
                    Toast.makeText(MainActivity.this, R.string.sin_app, Toast.LENGTH_SHORT).show();
                    return false;
                }
                return true;
            }
        });

        // La página avisa del color de fondo para pintar debajo de las barras del sistema.
        web.addJavascriptInterface(new Puente(), "SanesAndroid");

        if (estado == null || web.restoreState(estado) == null) {
            web.loadUrl(INICIO);
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    protected void onActivityResult(int peticion, int resultado, Intent datos) {
        if (peticion == ELEGIR_ARCHIVO && archivoPendiente != null) {
            archivoPendiente.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultado, datos));
            archivoPendiente = null;
            return;
        }
        super.onActivityResult(peticion, resultado, datos);
    }

    /** Color de las barras: oscuras en la portada azul, claras dentro de la app. */
    private class Puente {
        @JavascriptInterface
        public void barras(final String color, final boolean claras) {
            runOnUiThread(() -> {
                try {
                    raiz.setBackgroundColor(Color.parseColor(color));
                } catch (IllegalArgumentException e) {
                    return;
                }
                iconosOscuros(claras);
            });
        }
    }

    /** Con fondo claro, iconos oscuros en las barras; con fondo oscuro, claros. */
    @SuppressWarnings("deprecation")
    private void iconosOscuros(boolean si) {
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController c = getWindow().getInsetsController();
            if (c == null) return;
            int ambas = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                    | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
            c.setSystemBarsAppearance(si ? ambas : 0, ambas);
        } else {
            View decor = getWindow().getDecorView();
            int f = decor.getSystemUiVisibility();
            int claro = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= 26) claro |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            decor.setSystemUiVisibility(si ? (f | claro) : (f & ~claro));
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle salida) {
        super.onSaveInstanceState(salida);
        web.saveState(salida);
    }

    /** El botón «atrás» primero cierra ventanas y vuelve dentro de la app. */
    @SuppressWarnings("deprecation")
    @Override
    public void onBackPressed() {
        web.evaluateJavascript("(window.atras && window.atras()) === true", resultado -> {
            if (!"true".equals(resultado)) {
                finish();
            }
        });
    }

    @Override
    protected void onDestroy() {
        if (web != null) {
            web.destroy();
        }
        super.onDestroy();
    }
}
