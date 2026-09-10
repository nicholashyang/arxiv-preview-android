package com.example.arxivpreview.ui

import android.webkit.WebView
import com.example.arxivpreview.data.AppPreferences
import com.example.arxivpreview.data.local.HtmlProgressEntity
import org.json.JSONObject

internal fun applyReaderLayout(view: WebView, preferences: AppPreferences, after: (() -> Unit)? = null) {
    if (!isArxivHtml(android.net.Uri.parse(view.url ?: return))) return
    val mobile = preferences.readerMobile
    val css = if (!mobile) "" else """
        html {overflow-x:hidden!important} body {margin:0!important;padding:0 16px!important;box-sizing:border-box!important;font-size:${preferences.readerFont}px!important;line-height:${preferences.readerLine}!important}
        .ltx_page_main,.ltx_page_content,.ltx_document {width:100%!important;max-width:100%!important;margin:0!important;padding:0!important;float:none!important;display:block!important;column-count:1!important;box-sizing:border-box!important}
        .ltx_page_header,.ltx_page_footer,.ltx_page_navbar {display:none!important}
        .ltx_para,.ltx_p,.ltx_abstract {font-size:inherit!important;line-height:inherit!important;max-width:100%!important}
        p,li {overflow-wrap:break-word} h1,h2,h3,h4 {overflow-wrap:break-word;line-height:1.25!important}
        img,svg {max-width:100%!important;height:auto} figure,.ltx_figure {margin:16px 0!important;max-width:100%!important}
        .arxiv-scroll {max-width:100%;overflow-x:auto!important;overscroll-behavior-x:contain;padding:4px 0}
        .arxiv-scroll > * {max-width:none!important} .arxiv-scroll math {white-space:nowrap}
    """.trimIndent()
    view.evaluateJavascript("""(function(){
        let s=document.getElementById('arxiv-mobile-style');if(!s){s=document.createElement('style');s.id='arxiv-mobile-style';document.head.appendChild(s);}s.textContent=${JSONObject.quote(css)};
        const root=document.querySelector('.ltx_document')||document.querySelector('article')||document.body;
        root.querySelectorAll('h1,h2,h3,h4,p,.ltx_para').forEach((e,i)=>{if(!e.id)e.id='arxiv-reader-'+i;});
        if($mobile) root.querySelectorAll('.ltx_equationgroup,.ltx_equation,table,pre,math[display="block"]').forEach(e=>{
            if(e.closest('.arxiv-scroll') || e.parentElement.closest('.ltx_equationgroup'))return;
            const w=document.createElement('div');w.className='arxiv-scroll';e.parentNode.insertBefore(w,e);w.appendChild(e);
        });
        else document.querySelectorAll('.arxiv-scroll').forEach(w=>w.replaceWith(...w.childNodes));
        return true;
    })();""") { after?.invoke() }
}

internal val readPositionScript = """(function(){
    const root=document.querySelector('.ltx_document')||document.querySelector('article')||document.body;
    const nodes=Array.from(root.querySelectorAll('h1[id],h2[id],h3[id],h4[id],p[id],.ltx_para[id]'));
    let e=nodes.find(n=>n.getBoundingClientRect().bottom>0);
    const max=Math.max(1,document.documentElement.scrollHeight-innerHeight);
    return {anchor:e?e.id:'',offset:e?-e.getBoundingClientRect().top/Math.max(1,e.getBoundingClientRect().height):0,ratio:scrollY/max};
})();"""

internal fun restoreHtmlPosition(view: WebView, position: HtmlProgressEntity) {
    view.evaluateJavascript("""(function(){requestAnimationFrame(()=>{
        const e=document.getElementById(${JSONObject.quote(position.anchor)});
        const y=e?scrollY+e.getBoundingClientRect().top+${position.offset}*Math.max(1,e.getBoundingClientRect().height):${position.ratio}*Math.max(0,document.documentElement.scrollHeight-innerHeight);
        window.scrollTo(0,y);
    });})();""", null)
}
internal fun parseHtmlPosition(id: String, raw: String?): HtmlProgressEntity? = runCatching {
    val json = JSONObject(raw ?: return null)
    HtmlProgressEntity(id, json.optString("anchor"), json.optDouble("offset", 0.0).takeIf { it.isFinite() } ?: 0.0,
        json.optDouble("ratio", 0.0).takeIf { it.isFinite() }?.coerceIn(0.0, 1.0) ?: 0.0)
}.getOrNull()

internal val readerContentsScript = """(function(){const r=document.querySelector('.ltx_document')||document.querySelector('article')||document.body;return Array.from(r.querySelectorAll('h1,h2,h3,h4')).map(e=>({id:e.id,title:e.textContent.trim()})).filter(e=>e.id&&e.title);})();"""
