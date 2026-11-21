import {CommonModule} from "@angular/common";
import {Component,OnInit} from "@angular/core";
import {ActivatedRoute,Router} from "@angular/router";
import {ApiService} from "../core/api.service";
import {AuthService} from "../core/auth.service";

@Component({
  selector:"rk-sso-callback",
  standalone:true,
  imports:[CommonModule],
  template:`<div class="mx-auto max-w-lg p-10 text-center"><h1 class="text-2xl font-bold">{{error?"SSO sign-in failed":"Completing SSO sign-in…"}}</h1><p class="mt-2 text-sm text-slate-500">{{error||"Please wait."}}</p></div>`
})
export class SsoCallbackComponent implements OnInit {
  error="";
  constructor(private route:ActivatedRoute,private api:ApiService,private auth:AuthService,private router:Router){}

  ngOnInit(){
    const p=this.route.snapshot.queryParamMap;
    const provider=p.get("provider");
    const error=p.get("error");
    if(error){ this.error=error; return; }
    const code=p.get("code");
    if(provider === "saml") {
      if(!code){ this.error="Missing SAML handoff code."; return; }
      this.api.samlExchange(code).subscribe({
        next:r=>{this.auth.setSession(r);void this.router.navigateByUrl("/dashboard");},
        error:e=>this.error=e?.error?.message||"SAML sign-in could not be completed."
      });
      return;
    }
    const state=p.get("state");
    if(!code||!state){this.error="Missing SSO callback parameters.";return;}
    this.api.ssoCallback(code,state).subscribe({
      next:r=>{this.auth.setSession(r);void this.router.navigateByUrl("/dashboard");},
      error:e=>this.error=e?.error?.message||"SSO callback could not be completed."
    });
  }
}
