@import gg.jte.support.ForSupport
@import play.Play
@import play.exceptions.PlayException

@param PlayException exception
@param Play play
<style type="text/css">
    html, body, pre {
        margin: 0;
        padding: 0;
        font-family: Monaco, 'Lucida Console';
        background: #ECECEC;
    }
    h1 {
        margin: 0;
        background: #A31012;
        padding: 20px 45px;
        color: #fff;
        text-shadow: 1px 1px 1px rgba(0,0,0,.3);
        border-bottom: 1px solid #690000;
        font-size: 28px;
    }
    p#detail {
        margin: 0;
        padding: 15px 45px;
        background: #F5A0A0;
        border-top: 4px solid #D36D6D;
        color: #730000;
        text-shadow: 1px 1px 1px rgba(255,255,255,.3);
        font-size: 14px;
        border-bottom: 1px solid #BA7A7A;
    }
    p#detail input {
        background: -webkit-gradient(linear, 0% 0%, 0% 100%, from(#AE1113), to(#A31012));
        border: 1px solid #790000;
        padding: 3px 10px;
        text-shadow: 1px 1px 0 rgba(0, 0, 0, .5);
        color: white;
        border-radius: 3px;
        cursor: pointer;
        font-family: Monaco, 'Lucida Console';
        font-size: 12px;
        margin: 0 10px;
        display: inline-block;
        position: relative;
        top: -1px;
    }
    h2 {
        margin: 0;
        padding: 5px 45px;
        font-size: 12px;
        background: #333;
        color: #fff;
        text-shadow: 1px 1px 1px rgba(0,0,0,.3);
        border-top: 4px solid #2a2a2a;
    }
    pre {
        margin: 0;
        border-bottom: 1px solid #DDD;
        text-shadow: 1px 1px 1px rgba(255,255,255,.5);
        position: relative;
        font-size: 12px;
        overflow: hidden;
    }
    pre span.line {
        text-align: right;
        display: inline-block;
        padding: 5px 5px;
        width: 30px;
        background: #D6D6D6;
        color: #8B8B8B;
        text-shadow: 1px 1px 1px rgba(255,255,255,.5);
        font-weight: bold;
    }
    pre span.code {
        padding: 5px 5px;
        position: absolute;
        right: 0;
        left: 40px;
    }
    pre:first-child span.code {
        border-top: 4px solid #CDCDCD;
    }
    pre:first-child span.line {
        border-top: 4px solid #B6B6B6;
    }
    pre.error span.line {
        background: #A31012;
        color: #fff;
        text-shadow: 1px 1px 1px rgba(0,0,0,.3);
    }
    pre.error span.code {
        font-weight: bold;
    }
    pre.error {
        color: #A31012;
    }
    pre.error span.marker {
        background: #A31012;
        color: #fff;
        text-shadow: 1px 1px 1px rgba(0,0,0,.3);
    }
    #more {
        padding: 8px;
        font-size: 12px;
    }
    pre.error:hover {
        cursor: pointer;
    }
    pre.error:hover span.code {
        background-color: #D38989;
    }
</style>
@if(exception instanceof play.exceptions.PlayExceptionWithJavaSource)
    !{ var exception2 = (play.exceptions.PlayExceptionWithJavaSource) exception;}
    <h1>${exception2.getErrorTitle()}</h1>
    @if(play.mode.isDev())
        <p id="detail">${exception2.getErrorDescription()}</p>
    @endif
    @if(play.mode.isProd())
        <p>Error details are not displayed when Play! is in PROD mode. Check server logs for detail.</p>
    @endif
    @if(exception2.isSourceAvailable() && exception2.getLineNumber() != null && play.mode.isDev())
        <h2>In ${exception2.getSourceFile()} (around line ${exception2.getLineNumber()})</h2>
        !{var from = exception2.getLineNumber() - 5 >= 0 && exception2.getLineNumber() <= exception2.getSource().size() ? exception2.getLineNumber() - 5 : 0;}
        !{var to = exception2.getLineNumber() + 5  < exception2.getSource().size() ? exception2.getLineNumber() + 5 : exception2.getSource().size()-1;}
        @for(var line : ForSupport.of(exception2.getSource()))
            @if(exception2.getLineNumber() == line.getIndex()+from)
                <pre class="error" data-src="${exception2.getSourceFile()}" data-line="${exception2.getLineNumber()}"><span class="line">${line.getIndex()+from}</span><span class="code">&nbsp;${line.get().replace("&darr;", "<strong>&darr;</strong>").replace("\000", "<em>").replace("\001", "</em>")}</span></pre>
            @else
                <pre><span class="line">${line.getIndex()+from}</span><span class="code">&nbsp;${line.get().replace("&darr;", "<strong>&darr;</strong>").replace("\000", "<em>").replace("\001", "</em>")}</span></pre>
            @endif
        @endfor
    @endif
    <div id="more" class="block">
        This exception has been logged with id <strong>${exception2.getId()}</strong>
    </div>
@else
    <div id="header" class="block">
        <h1>${exception.getMessage()}</h1>
    </div>
@endif