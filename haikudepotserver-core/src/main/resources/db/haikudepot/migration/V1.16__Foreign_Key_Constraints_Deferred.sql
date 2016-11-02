-- ------------------------------------------------------
-- LOOKS LIKE SOME FOREIGN KEY CONSTRAINTS ARE NOT DEFERRED - FIX
-- ------------------------------------------------------

ALTER TABLE haikudepot.permission_user_pkg DROP CONSTRAINT permission_user_pkg_permission_id_fkey;
ALTER TABLE haikudepot.permission_user_pkg DROP CONSTRAINT permission_user_pkg_pkg_id_fkey;
ALTER TABLE haikudepot.permission_user_pkg DROP CONSTRAINT permission_user_pkg_user_id_fkey;
ALTER TABLE haikudepot.user_password_reset_token DROP CONSTRAINT user_password_reset_token_user_id_fkey;

ALTER TABLE haikudepot.permission_user_pkg ADD CONSTRAINT permission_user_pkg_permission_id_fkey FOREIGN KEY (permission_id) REFERENCES permission(id) DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE haikudepot.permission_user_pkg ADD CONSTRAINT permission_user_pkg_pkg_id_fkey FOREIGN KEY (pkg_id) REFERENCES pkg(id) DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE haikudepot.permission_user_pkg ADD CONSTRAINT permission_user_pkg_user_id_fkey FOREIGN KEY (user_id) REFERENCES "user"(id) DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE haikudepot.user_password_reset_token ADD CONSTRAINT user_password_reset_token_user_id_fkey FOREIGN KEY (user_id) REFERENCES "user"(id) DEFERRABLE INITIALLY DEFERRED;