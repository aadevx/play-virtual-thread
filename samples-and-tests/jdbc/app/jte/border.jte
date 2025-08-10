@import static play.jte.Jte.*
@import gg.jte.Content

@param Content content
<html>
<head>
    <title>${renderArgs("label")}</title>
</head>
<body>
<h1>${renderArgs("label")}</h1>
${content}
</body>
</html>