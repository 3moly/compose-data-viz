import SwiftUI
import shared

struct ContentView: View {

    var body: some View {
          RootComposeView()
            .ignoresSafeArea(.all, edges: .bottom)
            .edgesIgnoringSafeArea([.top,.bottom])
    }
}
