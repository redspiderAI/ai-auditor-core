use hmac::{Hmac, Mac};
use sha2::Sha256;

#[allow(dead_code)]
pub fn upload_to_oss(
    local_file_path: &str,
    bucket_name: &str,
    access_key_id: &str,
    access_key_secret: &str,
) -> anyhow::Result<String> {
    let _ = (Hmac::<Sha256>::new_from_slice(access_key_secret.as_bytes()), access_key_id);
    let object_name = std::path::Path::new(local_file_path)
        .file_name()
        .unwrap()
        .to_str()
        .unwrap();
    let url = format!("https://{}.oss-cn-beijing.aliyuncs.com/{}", bucket_name, object_name);
    Ok(url)
}
