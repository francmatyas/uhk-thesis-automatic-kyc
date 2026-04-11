import { cn } from "@/lib/utils";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { useState } from "react";
import { useNavigate, useSearchParams } from "react-router";
import { InputNewPassword } from "../ui/input-password";
import { Link } from "react-router";

import { register } from "@/api/auth";
import { sha256 } from "js-sha256";
import { useAuth } from "@/contexts/AuthContext";
import { getPostLoginRedirect } from "@/router/authRedirect";
import { toProviderPath } from "@/router/scope";

export default function RegisterForm({ className, ...props }) {
  const [form, setForm] = useState({});
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const { setProfile } = useAuth();

  function handleChange(e) {
    setForm({ ...form, [e.target.name]: e.target.value });
  }

  function handleRegister(e) {
    e.preventDefault();

    if (loading || !form.email || !form.password) return;

    setLoading(true);
    register(form.givenName, form.familyName, form.email, sha256(form.password))
      .then((res) => {
        setProfile(res.data);
        const redirectTo = getPostLoginRedirect(searchParams);
        navigate(redirectTo || toProviderPath("/"), { replace: true });
      })
      .catch((err) => {
        console.error(err);
      })
      .finally(() => setLoading(false));
  }

  return (
    <div className={cn("flex flex-col gap-6", className)} {...props}>
      <Card className="overflow-hidden p-0">
        <CardContent className="grid p-0 md:grid-cols-2">
          <form
            id="registerForm"
            className="p-6 md:p-8"
            onSubmit={handleRegister}
          >
            <div className="flex flex-col gap-6">
              <div className="flex flex-col items-center text-center">
                <h1 className="text-2xl font-bold">Get Started Now</h1>
                <p className="text-balance text-muted-foreground"></p>
              </div>
              <div className="flex gap-2">
                <div className="grid gap-2">
                  <Label htmlFor="givenName">Name</Label>
                  <Input
                    id="givenName"
                    type="text"
                    name="givenName"
                    required
                    onChange={handleChange}
                    value={form.givenName}
                  />
                </div>
                <div className="grid gap-2">
                  <Label htmlFor="familyName">Surname</Label>
                  <Input
                    id="familyName"
                    type="text"
                    name="familyName"
                    required
                    onChange={handleChange}
                    value={form.familyName}
                  />
                </div>
              </div>
              <div className="grid gap-2">
                <Label htmlFor="email">Email</Label>
                <Input
                  id="email"
                  type="email"
                  name="email"
                  placeholder="m@example.com"
                  required
                  onChange={handleChange}
                  value={form.email}
                />
              </div>
              <div className="grid gap-2">
                <div className="flex items-center">
                  <Label htmlFor="password">Password</Label>
                </div>
                <InputNewPassword
                  id="password"
                  name="password"
                  required
                  onChange={handleChange}
                  value={form.password}
                />
              </div>
              <Button type="submit" className="w-full cursor-pointer">
                Sign Up
              </Button>     
              <div className="text-center text-sm">
                Already have an account?{" "}
                <Link to="/login" className="underline underline-offset-4">
                  Sign In
                </Link>
              </div>
            </div>
          </form>
          <div className="relative hidden bg-muted md:block">
            <img
              src="/placeholder.svg"
              alt="Image"
              className="absolute inset-0 h-full w-full object-cover dark:brightness-[0.2] dark:grayscale"
            />
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
