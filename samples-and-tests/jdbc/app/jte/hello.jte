@import controllers.Application
@import models.Berita
@import static play.jte.Jte.*

@param String message
@param String label
@param long time


@template.border(content = @`
<p>Hello  ${message}!</p>
<a href="${action(Application.class, "delete", "download0.pdf")}">link</a>
  ${label} : ${time}
`)




