use hmac::{Hmac, Mac};
use sha2::Sha256;
use std::time::{SystemTime, UNIX_EPOCH};

type HmacSha256 = Hmac<Sha256>;

pub fn upload_to_oss(
    local_file_path: &str,
    bucket_name: &str,
    access_key_id: &str,
    access_key_secret: &str,
) -> anyhow::Result<String> {
    // 简化：实际需构造签名 URL
    // 此处仅返回模拟 URL
    let object_name = std::path::Path::new(local_file_path)
        .file_name()
        .unwrap()
        .to_str()
        .unwrap();
    let url = format!(
        "https://{}.oss-cn-beijing.aliyuncs.com/{}",
        bucket_name, object_name
    );
    Ok(url)
}