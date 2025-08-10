@import java.lang.Exception
@import play.Play

@param Exception result
@param Play play
<html lang="en">
<head>
    <title>Not found</title>
    <meta http-equiv="Content-Type" content="text/html; charset=utf-8"/>
</head>
<body>
@if(play.mode.isDev())
    @template.tags.404(result = result)
@else
<h1>Not found</h1>
<p>
    ${result.getMessage()}
</p>
@endif
</body>
</html>

