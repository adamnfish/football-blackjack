import lustre
import lustre/attribute
import lustre/element/html as html

pub fn main() {
  let app =
    lustre.element(
      html.main(
        [
          attribute.class(
            "min-h-screen bg-slate-950 text-slate-100 flex items-center justify-center px-6",
          ),
        ],
        [
          html.div(
            [
              attribute.class(
                "max-w-xl rounded-2xl border border-slate-800 bg-slate-900/80 p-10 text-center shadow-2xl",
              ),
            ],
            [
              html.h1(
                [attribute.class("text-4xl font-bold tracking-tight text-cyan-300")],
                [html.text("Hello, world!")],
              ),
              html.p(
                [attribute.class("mt-3 text-slate-300")],
                [html.text("Football Blackjack + Lustre + Tailwind CSS 4.3")],
              ),
            ],
          ),
        ],
      ),
    )

  let assert Ok(_) = lustre.start(app, "#app", Nil)
}
