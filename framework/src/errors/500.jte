@import play.exceptions.PlayException
@import play.Play

@param PlayException exception
@param Play play
<!DOCTYPE html>
<html>
    <head>
        <title>Application error</title>
        <meta http-equiv="Content-Type" content="text/html; charset=utf-8"/>
    </head>
    <body>
        @if(play.mode.isDev())
           @template.tags.500(exception = exception, play = play)
        @else
            <h1>Oops, an error occurred</h1>
            <p>
                This exception has been logged with id <strong>${exception.getId()}</strong>.
            </p>
        @endif
    </body>
</html>
