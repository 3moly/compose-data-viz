import SwiftUI
import shared

@main
struct iOSApp: App {
    
    @UIApplicationDelegateAdaptor(AppDelegate.self)
     var appDelegate
     
    
    var fixedPreferredContentSizeCategory: UIContentSizeCategory {
           return .small
       }
      
    var body: some Scene {
        WindowGroup {
            
            let container = ZStack{
                ContentView()
                 
            }
            
            if #available(iOS 15, *) {
                container.dynamicTypeSize(.large ... .large)
            } else {
                container
            }
        }
    }
}
